package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.BeaconReading
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.binaryStatus
import com.boatspark.app.core.model.formatDecimal
import com.boatspark.app.core.model.formatTemperature
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.core.model.powerSource
import com.boatspark.app.core.settings.TemperatureUnit
import com.boatspark.app.ui.EmptyState
import com.boatspark.app.ui.SectionHeader
import java.time.Instant

@Composable
fun BoatStatusScreen(
    cachedStatus: CachedValue<BoatStatus>?,
    osmTileUrl: String,
    seamarkTileUrl: String,
    temperatureUnit: TemperatureUnit,
    onAlerts: (Long) -> Unit,
) {
    val status = cachedStatus?.value
    if (status == null) {
        EmptyState("Status unavailable", "No cached telemetry is available for this boat.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
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

        val shownGps = status.gps ?: status.lastGps?.gps
        if (shownGps != null) {
            item {
                key(status.boatId) {
                    BoatMap(
                        gps = shownGps,
                        isLastKnown = status.gps == null,
                        osmTileUrl = osmTileUrl,
                        seamarkTileUrl = seamarkTileUrl,
                    )
                }
            }
            item {
                StatusCard("Location") {
                    ValueRow(
                        if (status.gps == null) "Last known position" else "Current position",
                        "${formatDecimal(shownGps.latitudeDegrees, 6)}, ${formatDecimal(shownGps.longitudeDegrees, 6)}",
                    )
                    ValueRow("Satellites", shownGps.satellites.toString())
                    ValueRow("Course", "${shownGps.courseOverGround}°")
                    ValueRow("Speed", "${formatDecimal(shownGps.speedKnots, 2)} kn")
                    ValueRow("PDOP", formatDecimal(shownGps.pdopValue, 2))
                }
            }
        } else {
            item {
                StatusCard("Location") {
                    Text(if (status.gpsNoFix) "No GPS fix is available." else "No GPS data is available.")
                }
            }
        }

        status.gpio?.let { gpio ->
            item {
                StatusCard("Electrical and machinery") {
                    ValueRow("Battery 1", "${formatDecimal(gpio.battery1Volts, 2)} V")
                    ValueRow("Battery 2", "${formatDecimal(gpio.battery2Volts, 2)} V")
                    ValueRow("Shore power", binaryStatus(gpio.shorePowerStatus))
                    ValueRow("Bilge pump", binaryStatus(gpio.bilgePumpStatus))
                    ValueRow("Engine", binaryStatus(gpio.engineStatus))
                }
            }
        }

        status.system?.let { system ->
            item {
                StatusCard("Device") {
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

        if (status.beacons.isNotEmpty() || status.missingBeacons.isNotEmpty()) {
            item { Text("Sensors", style = MaterialTheme.typography.titleLarge) }
            // The API doesn't guarantee unique beacon IDs, so the position keeps keys unique.
            status.beacons.forEachIndexed { index, beacon ->
                item(key = "beacon-$index-${beacon.beaconId}") {
                    BeaconCard(beacon, subtitle = "Current", temperatureUnit = temperatureUnit)
                }
            }
            status.missingBeacons.forEachIndexed { index, missing ->
                item(key = "missing-$index-${missing.beacon.beaconId}") {
                    BeaconCard(
                        missing.beacon,
                        subtitle = "Last seen ${formatTimestamp(missing.publishedAt)}",
                        temperatureUnit = temperatureUnit,
                    )
                }
            }
        }
    }
}

@Composable
private fun BeaconCard(
    beacon: BeaconReading,
    subtitle: String,
    temperatureUnit: TemperatureUnit,
) {
    StatusCard(beacon.beaconName) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        ValueRow("Temperature", formatTemperature(beacon.temperatureCelsius, temperatureUnit))
        ValueRow("Humidity", "${formatDecimal(beacon.humidityPercent, 2)}%")
        ValueRow("Battery", "${beacon.batteryPercentage}%")
        ValueRow("Signal", "${beacon.rssi} dBm")
    }
}

@Composable
private fun StatusCard(
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
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
