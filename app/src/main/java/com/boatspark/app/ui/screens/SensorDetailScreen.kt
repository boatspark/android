package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boatspark.app.HistoryState
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.formatDecimal
import com.boatspark.app.core.model.formatTemperature
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.core.model.temperatureChart
import com.boatspark.app.core.settings.TemperatureUnit
import com.boatspark.app.ui.EmptyState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun SensorDetailScreen(
    cachedStatus: CachedValue<BoatStatus>?,
    beaconId: Long,
    history: HistoryState?,
    temperatureUnit: TemperatureUnit,
) {
    val status = cachedStatus?.value
    val current = status?.beacons?.firstOrNull { it.beaconId == beaconId }
    val missing = if (current == null) status?.missingBeacons?.firstOrNull { it.beacon.beaconId == beaconId } else null
    val beacon = current ?: missing?.beacon
    if (status == null || beacon == null) {
        EmptyState("Sensor unavailable", "No cached reading is available for this sensor.")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(beacon.beaconName, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (missing != null) {
                "Last seen ${formatTimestamp(missing.publishedAt)}"
            } else {
                "Reading ${formatTimestamp(status.publishedAt)}"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        StatusCard("Current values") {
            ValueRow("Temperature", formatTemperature(beacon.temperatureCelsius, temperatureUnit))
            ValueRow("Humidity", "${formatDecimal(beacon.humidityPercent, 0)}%")
            ValueRow(
                "Battery",
                "${beacon.batteryPercentage}% (${formatDecimal(beacon.batteryVoltage / 1000.0, 2)} V)",
            )
            ValueRow("Signal strength", "${beacon.rssi} dBm")
        }
        val style = remember(temperatureUnit) {
            ChartStyle(
                description = "Temperature history chart",
                yRange = { values -> floor(values.min()) - 2..ceil(values.max()) + 2 },
                yFormatter = CartesianValueFormatter.decimal(
                    decimalCount = 0,
                    suffix = if (temperatureUnit == TemperatureUnit.Celsius) "°C" else "°F",
                ),
            )
        }
        StatusCard("Temperature, last 48 hours") {
            HistoryCardContent(
                history = history,
                emptyText = "No temperature readings in the last 48 hours.",
                style = style,
                chartFor = { temperatureChart(it, beaconId, temperatureUnit) },
            )
        }
    }
}
