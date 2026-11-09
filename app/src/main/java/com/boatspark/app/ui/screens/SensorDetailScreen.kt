package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boatspark.app.HistoryState
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.TemperatureChart
import com.boatspark.app.core.model.formatDecimal
import com.boatspark.app.core.model.formatTemperature
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.core.model.temperatureChart
import com.boatspark.app.core.settings.TemperatureUnit
import com.boatspark.app.ui.EmptyState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        StatusCard("Temperature, last 48 hours") {
            when (history) {
                null, HistoryState.Loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                is HistoryState.Failed -> Text("History unavailable: ${history.message}")
                is HistoryState.Loaded -> {
                    val chart = remember(history.history, beaconId, temperatureUnit) {
                        temperatureChart(history.history, beaconId, temperatureUnit)
                    }
                    if (chart == null || chart.segments.isEmpty()) {
                        Text("No temperature readings in the last 48 hours.")
                    } else {
                        TemperatureHistoryChart(chart, temperatureUnit)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryChart(chart: TemperatureChart, unit: TemperatureUnit) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(chart) {
        modelProducer.runTransaction {
            lineModel {
                chart.segments.forEach { segment ->
                    series(segment.map { it.bucket }, segment.map { it.temperature })
                }
            }
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val labelSpacing = (LABEL_HOURS * 60 / chart.intervalMinutes).coerceAtLeast(1)
    val bottomFormatter = remember(chart) {
        CartesianValueFormatter { _, value, _ ->
            chart.origin
                .plusSeconds((value * chart.intervalMinutes * 60).toLong())
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
        }
    }
    val startFormatter = remember(unit) {
        CartesianValueFormatter.decimal(decimalCount = 0, suffix = if (unit == TemperatureUnit.Celsius) "°C" else "°F")
    }
    // Put labels on local 6-hour marks (00:00, 06:00, ...) rather than wherever the window starts.
    val labelOffset = remember(chart) {
        (0 until labelSpacing).firstOrNull { index ->
            val time = chart.origin.plusSeconds(index * chart.intervalMinutes * 60L).atZone(ZoneId.systemDefault())
            time.minute == 0 && time.hour % LABEL_HOURS == 0
        } ?: 0
    }
    val rangeProvider = remember(chart) {
        val temperatures = chart.segments.flatten().map { it.temperature }
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = (chart.bucketCount - 1).toDouble(),
            minY = floor(temperatures.min()) - 2,
            maxY = ceil(temperatures.max()) + 2,
        )
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val primary = MaterialTheme.colorScheme.primary
        val line = LineCartesianLayer.rememberLine(
            fill = remember(primary) { LineCartesianLayer.LineFill.single(Fill(primary)) },
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = bottomFormatter,
                    itemPlacer = remember(labelSpacing, labelOffset) {
                        EdgeTrimmingItemPlacer(
                            HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }, offset = { labelOffset }),
                        )
                    },
                ),
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics { contentDescription = "Temperature history chart" },
        )
    }
}

/**
 * Drops labels too near either edge to fit. Vico gives an edge label twice its distance to the
 * edge as width and would otherwise truncate it to "…".
 */
private class EdgeTrimmingItemPlacer(
    private val base: HorizontalAxis.ItemPlacer,
) : HorizontalAxis.ItemPlacer by base {
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val pixelsPerX = context.layerDimensions.xSpacing / context.ranges.xStep
        val margin = maxLabelWidth / 2 / pixelsPerX
        return base.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)
            .filter { it - fullXRange.start >= margin && fullXRange.endInclusive - it >= margin }
    }
}

private val CHART_HEIGHT = 220.dp
private const val LABEL_HOURS = 6
