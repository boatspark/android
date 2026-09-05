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
import com.boatspark.app.core.model.GpsData
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

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
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = position, zoom = 13.0),
    )
    val mapContent: @Composable () -> Unit = {
        val osm = rememberRasterSource(
            tiles = listOf(osmTileUrl),
            options = TileSetOptions(
                minZoom = 0,
                maxZoom = 19,
                attributionHtml = "© OpenStreetMap contributors",
            ),
            tileSize = 256,
        )
        RasterLayer(id = "openstreetmap", source = osm)

        val seamarks = rememberRasterSource(
            tiles = listOf(seamarkTileUrl),
            options = TileSetOptions(
                minZoom = 0,
                maxZoom = 18,
                attributionHtml = "© OpenSeaMap",
            ),
            tileSize = 256,
        )
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
            baseStyle = BaseStyle.Empty,
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            content = mapContent,
        )
        Button(
            onClick = {
                coroutineScope.launch {
                    cameraState.animateTo(
                        cameraState.position.copy(target = position, zoom = 13.0),
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
