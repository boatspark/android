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
import com.boatspark.app.core.model.GpioMetric
import com.boatspark.app.core.model.formatTimestamp
import com.boatspark.app.core.model.gpioChart
import com.boatspark.app.core.model.gpioEventCount
import com.boatspark.app.ui.EmptyState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun ElectricalDetailScreen(
    cachedStatus: CachedValue<BoatStatus>?,
    metric: GpioMetric,
    history: HistoryState?,
) {
    val status = cachedStatus?.value
    val gpio = status?.gpio
    if (gpio == null) {
        EmptyState("${metric.title} unavailable", "No cached reading is available.")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(metric.title, style = MaterialTheme.typography.headlineSmall)
        Text("Reading ${formatTimestamp(status.publishedAt)}", style = MaterialTheme.typography.bodySmall)
        StatusCard("Current value") {
            ValueRow(if (metric.isBinary) "State" else "Voltage", metric.display(gpio))
        }
        val style = remember(metric) { chartStyle(metric) }
        StatusCard("${if (metric.isBinary) "State" else "Voltage"}, last 48 hours") {
            HistoryCardContent(
                history = history,
                emptyText = "No readings in the last 48 hours.",
                style = style,
                chartFor = { gpioChart(it, metric) },
                footer = { boatHistory ->
                    gpioEventCount(boatHistory, metric)?.let { count ->
                        Text(eventSummary(metric, count), style = MaterialTheme.typography.bodyMedium)
                    }
                },
            )
        }
    }
}

private fun chartStyle(metric: GpioMetric): ChartStyle = if (metric.isBinary) {
    ChartStyle(
        description = "${metric.title} history chart",
        yRange = { 0.0..1.0 },
        yFormatter = CartesianValueFormatter { _, value, _ -> if (value >= 0.5) "On" else "Off" },
        yStep = 1.0,
        stepped = true,
    )
} else {
    ChartStyle(
        description = "${metric.title} history chart",
        // Half-volt bounds with a margin, so small changes stay readable.
        yRange = { values -> floor(values.min() * 2) / 2 - 0.5..ceil(values.max() * 2) / 2 + 0.5 },
        yFormatter = CartesianValueFormatter.decimal(decimalCount = 1, suffix = " V"),
    )
}

internal fun eventSummary(metric: GpioMetric, count: Int): String {
    val times = if (count == 1) "1 time" else "$count times"
    return if (metric == GpioMetric.ShorePower) {
        "Lost $times in the last 48 hours."
    } else {
        "Turned on $times in the last 48 hours."
    }
}
