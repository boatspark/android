package com.boatspark.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.BeaconReading
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.GpioMetric
import com.boatspark.app.core.model.batteryThirds
import com.boatspark.app.core.model.formatDecimal
import com.boatspark.app.core.model.formatTemperature
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.core.model.powerSource
import com.boatspark.app.core.settings.StatusSection
import com.boatspark.app.core.settings.TemperatureUnit
import com.boatspark.app.ui.EmptyState
import com.boatspark.app.ui.SectionHeader
import java.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun BoatStatusScreen(
    cachedStatus: CachedValue<BoatStatus>?,
    osmTileUrl: String,
    seamarkTileUrl: String,
    temperatureUnit: TemperatureUnit,
    expandedSections: Set<StatusSection>,
    onSectionExpandedChange: (StatusSection, Boolean) -> Unit,
    onAlerts: (Long) -> Unit,
    onSensor: (Long) -> Unit,
    onElectrical: (GpioMetric) -> Unit,
) {
    val status = cachedStatus?.value
    if (status == null) {
        EmptyState("Status unavailable", "No cached telemetry is available for this boat.")
        return
    }

    LazyVerticalGrid(
        // At least 150dp per tile: two across on a portrait phone, more when wider.
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidthItem {
            Column {
                SectionHeader(
                    title = status.boatName,
                    action = "View alerts",
                    onAction = { onAlerts(status.boatId) },
                )
                Text(
                    "Reading ${formatTimestamp(status.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Cached ${formatTimestamp(Instant.ofEpochMilli(cachedStatus.refreshedAt).toString())}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val shownGps = status.gps ?: status.lastGps?.gps
        if (shownGps != null) {
            fullWidthItem {
                key(status.boatId) {
                    BoatMap(
                        gps = shownGps,
                        isLastKnown = status.gps == null,
                        osmTileUrl = osmTileUrl,
                        seamarkTileUrl = seamarkTileUrl,
                    )
                }
            }
        }
        fullWidthItem {
            CollapsibleCard(
                title = "Location",
                expanded = StatusSection.Location in expandedSections,
                onExpandedChange = { onSectionExpandedChange(StatusSection.Location, it) },
            ) {
                if (shownGps != null) {
                    ValueRow(
                        if (status.gps == null) "Last known position" else "Current position",
                        "${formatDecimal(shownGps.latitudeDegrees, 6)}, ${formatDecimal(shownGps.longitudeDegrees, 6)}",
                    )
                    ValueRow("Satellites", shownGps.satellites.toString())
                    ValueRow("Course", "${shownGps.courseOverGround}°")
                    ValueRow("Speed", "${formatDecimal(shownGps.speedKnots, 2)} kn")
                    ValueRow("PDOP", formatDecimal(shownGps.pdopValue, 2))
                } else {
                    Text(if (status.gpsNoFix) "No GPS fix is available." else "No GPS data is available.")
                }
            }
        }

        status.gpio?.let { gpio ->
            fullWidthItem { SectionTitle("Power") }
            listOf(GpioMetric.Battery1, GpioMetric.Battery2, GpioMetric.ShorePower).forEach { metric ->
                item(key = "gpio-${metric.routeKey}") {
                    ValueTile(metric.title, metric.display(gpio), onClick = { onElectrical(metric) })
                }
            }
            fullWidthItem { SectionTitle("Sensors") }
            listOf(GpioMetric.BilgePump, GpioMetric.Engine).forEach { metric ->
                item(key = "gpio-${metric.routeKey}") {
                    ValueTile(metric.title, metric.display(gpio), onClick = { onElectrical(metric) })
                }
            }
        }

        if (status.beacons.isNotEmpty() || status.missingBeacons.isNotEmpty()) {
            fullWidthItem { SectionTitle("Temperature Sensors") }
            // The API doesn't guarantee unique beacon IDs, so the position keeps keys unique.
            status.beacons.forEachIndexed { index, beacon ->
                item(key = "beacon-$index-${beacon.beaconId}") {
                    SensorTile(beacon, temperatureUnit, lastSeen = null, onClick = { onSensor(beacon.beaconId) })
                }
            }
            status.missingBeacons.forEachIndexed { index, missing ->
                item(key = "missing-$index-${missing.beacon.beaconId}") {
                    SensorTile(
                        missing.beacon,
                        temperatureUnit,
                        lastSeen = formatTimestamp(missing.publishedAt),
                        onClick = { onSensor(missing.beacon.beaconId) },
                    )
                }
            }
        }

        status.system?.let { system ->
            fullWidthItem {
                CollapsibleCard(
                    title = "Device",
                    expanded = StatusSection.Device in expandedSections,
                    onExpandedChange = { onSectionExpandedChange(StatusSection.Device, it) },
                ) {
                    ValueRow("Power source", powerSource(system.powerSource))
                    ValueRow(
                        "Device battery",
                        system.batteryPercent?.let { "${formatDecimal(it)}%" } ?: "Disconnected",
                    )
                    ValueRow("Signal strength", "${formatDecimal(system.signalStrengthPercent)}%")
                    ValueRow("Signal quality", "${formatDecimal(system.signalQualityPercent)}%")
                    ValueRow("Free memory", "${system.freeMemory} bytes")
                    ValueRow("Firmware", status.firmwareVersion?.toString() ?: "Unavailable")
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ValueTile(title: String, value: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SensorTile(
    beacon: BeaconReading,
    temperatureUnit: TemperatureUnit,
    lastSeen: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (lastSeen != null) Modifier.alpha(0.55f) else Modifier),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    beacon.beaconName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 28.dp),
                )
                Text(
                    formatTemperature(beacon.temperatureCelsius, temperatureUnit),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "${formatDecimal(beacon.humidityPercent, 0)}% humidity",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (lastSeen != null) {
                    Text(
                        "Last seen $lastSeen",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            BatteryIcon(
                beacon.batteryPercentage,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 12.dp),
            )
        }
    }
}

@Composable
internal fun BatteryIcon(percentage: Int, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (batteryThirds(percentage)) {
        3 -> Icons.Filled.Battery6Bar
        2 -> Icons.Filled.Battery4Bar
        1 -> Icons.Filled.Battery2Bar
        else -> Icons.Filled.BatteryUnknown
    }
    Icon(icon, contentDescription = "Battery $percentage%", modifier = modifier)
}

@Composable
private fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibleState = remember { MutableTransitionState(expanded) }
    visibleState.targetState = expanded
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var userExpanded by remember { mutableStateOf(false) }
    var cardHeight by remember { mutableIntStateOf(0) }
    // Once the user expands the card, keep it in view as it grows so the revealed content
    // scrolls on screen along with the expand animation.
    LaunchedEffect(userExpanded) {
        if (!userExpanded) return@LaunchedEffect
        coroutineScope {
            val following = launch {
                snapshotFlow { cardHeight }.collectLatest { bringIntoViewRequester.bringIntoView() }
            }
            snapshotFlow { visibleState.isIdle && visibleState.currentState }.first { it }
            following.cancel()
        }
        bringIntoViewRequester.bringIntoView()
        userExpanded = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { cardHeight = it.height },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    userExpanded = !expanded
                    onExpandedChange(!expanded)
                }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visibleState) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun StatusCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
