package com.boatspark.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.boatspark.app.BuildConfig
import com.boatspark.app.core.model.GpsData
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.MapRuntime
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterTileSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import java.time.DateTimeException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.time.Instant as MapInstant

// The OpenStreetMap tile policy requires an identifying User-Agent.
private val mapRuntime: MapRuntime by lazy {
    val userAgent = "BoatSpark/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
    val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    createMapRuntime {
        resourceProvider = MapResourceProvider(
            accepts = { it.kind == MapResourceKind.Tile },
            load = { request ->
                val httpRequest = Request.Builder()
                    .url(request.url)
                    .header("User-Agent", userAgent)
                    .apply {
                        if (request.priorData != null) {
                            request.priorEtag?.let { header("If-None-Match", it) }
                            request.priorModified?.let {
                                val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                    java.time.Instant.ofEpochMilli(it.toEpochMilliseconds())
                                        .atZone(ZoneOffset.UTC),
                                )
                                header("If-Modified-Since", date)
                            }
                        }
                    }
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    val modified = response.header("Last-Modified").toMapInstantOrNull()
                    val expires = response.cacheExpiresAt()
                    when {
                        response.code == 304 && request.priorData != null ->
                            MapResourceLoad.NotModified(modified = modified, expires = expires)
                        response.code == 204 -> MapResourceLoad.NoContent()
                        response.isSuccessful -> {
                            val bytes = response.body?.bytes()
                            if (bytes == null) {
                                MapResourceLoad.NoContent()
                            } else {
                                MapResourceLoad.Bytes(
                                    bytes = bytes,
                                    etag = response.header("ETag"),
                                    mustRevalidate = response.cacheControl.noCache ||
                                        response.cacheControl.mustRevalidate,
                                    modified = modified,
                                    expires = expires,
                                )
                            }
                        }
                        else -> MapResourceLoad.Failed(
                            reason = when {
                                response.code == 404 -> MapResourceError.NotFound
                                response.code == 429 -> MapResourceError.RateLimit
                                response.code in 500..599 -> MapResourceError.Server
                                else -> MapResourceError.Other
                            },
                            message = response.message.ifBlank { "HTTP ${response.code}" },
                        )
                    }
                }
            },
        )
    }
}

private fun String?.toMapInstantOrNull(): MapInstant? {
    if (this == null) return null
    return try {
        MapInstant.fromEpochMilliseconds(
            ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli(),
        )
    } catch (_: DateTimeException) {
        null
    }
}

private fun okhttp3.Response.cacheExpiresAt(): MapInstant? {
    val maxAgeSeconds = cacheControl.maxAgeSeconds
    if (maxAgeSeconds != -1) {
        return MapInstant.fromEpochMilliseconds(
            System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(maxAgeSeconds.toLong()),
        )
    }
    return header("Expires").toMapInstantOrNull()
}

@Composable
fun BoatMap(
    gps: GpsData,
    isLastKnown: Boolean,
    osmTileUrl: String,
    seamarkTileUrl: String,
) {
    val position = Position(
        longitude = gps.longitudeDegrees,
        latitude = gps.latitudeDegrees,
    )
    val mapState = rememberMapState(
        runtime = mapRuntime,
        baseStyle = BaseStyle.Empty,
        initialCameraPosition = CameraPosition(center = position, zoom = 13.0),
    ) {
        val osm = rememberRasterTileSource(tiles = listOf(osmTileUrl), tileSize = 256) {
            maxZoom = 19
            attributionHtml = "© OpenStreetMap contributors"
        }
        RasterLayer(id = "openstreetmap", source = osm)

        val seamarks = rememberRasterTileSource(tiles = listOf(seamarkTileUrl), tileSize = 256) {
            maxZoom = 18
            attributionHtml = "© OpenSeaMap"
        }
        RasterLayer(id = "openseamap-seamarks", source = seamarks)

        val marker = rememberGeoJsonSource(
            GeoJsonData.Features(Feature(geometry = Point(position), properties = null)),
        )
        CircleLayer(
            id = "boat-position",
            source = marker,
            radius = const(9.dp),
            color = const(if (isLastKnown) Color(0xFFFFA000) else Color(0xFFF04523)),
            strokeWidth = const(3.dp),
            strokeColor = const(Color.White),
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
    ) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            // Attribution is shown in the bar below the map instead of over it.
            overlay = { include(MapOverlay.None) },
        )
        Button(
            onClick = {
                coroutineScope.launch {
                    mapState.animateCamera(
                        CameraUpdate(center = position, zoom = 13.0, bearing = 0.0, pitch = 0.0),
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Text("Recenter")
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isLastKnown) "Last known location • Not for navigation" else "Not for navigation",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
            Row {
                TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
                    Text("© OSM")
                }
                TextButton(onClick = { uriHandler.openUri("https://www.openseamap.org/") }) {
                    Text("© OpenSeaMap")
                }
            }
        }
    }
}
