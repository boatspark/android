package com.boatspark.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boatspark.app.HistoryState
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.HistoryChart
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

/** How a history chart draws its y axis and line. */
internal class ChartStyle(
    val description: String,
    val yRange: (List<Double>) -> ClosedFloatingPointRange<Double>,
    val yFormatter: CartesianValueFormatter,
    val yStep: Double? = null,
    /** Draws held on/off states with a filled area rather than a sloped line between samples. */
    val stepped: Boolean = false,
)

/** The 48-hour history card body: loading, failure, empty or the chart. */
@Composable
internal fun HistoryCardContent(
    history: HistoryState?,
    emptyText: String,
    style: ChartStyle,
    chartFor: (BoatHistory) -> HistoryChart?,
    footer: @Composable (BoatHistory) -> Unit = {},
) {
    when (history) {
        null, HistoryState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is HistoryState.Failed -> Text("History unavailable: ${history.message}")
        is HistoryState.Loaded -> {
            val chart = remember(history.history, chartFor) { chartFor(history.history) }
            if (chart == null || chart.segments.isEmpty()) {
                Text(emptyText)
            } else {
                HistoryLineChart(chart, style)
                footer(history.history)
            }
        }
    }
}

@Composable
private fun HistoryLineChart(chart: HistoryChart, style: ChartStyle) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(chart) {
        modelProducer.runTransaction {
            lineModel {
                chart.segments.forEach { segment ->
                    series(segment.map { it.bucket }, segment.map { it.value })
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
    // Put labels on local 6-hour marks (00:00, 06:00, ...) rather than wherever the window starts.
    val labelOffset = remember(chart) {
        (0 until labelSpacing).firstOrNull { index ->
            val time = chart.origin.plusSeconds(index * chart.intervalMinutes * 60L).atZone(ZoneId.systemDefault())
            time.minute == 0 && time.hour % LABEL_HOURS == 0
        } ?: 0
    }
    val rangeProvider = remember(chart, style) {
        val yRange = style.yRange(chart.segments.flatten().map { it.value })
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = (chart.bucketCount - 1).toDouble(),
            minY = yRange.start,
            maxY = yRange.endInclusive,
        )
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val primary = MaterialTheme.colorScheme.primary
        val line = if (style.stepped) {
            LineCartesianLayer.rememberLine(
                fill = remember(primary) { LineCartesianLayer.LineFill.single(Fill(primary)) },
                areaFill = remember(primary) { LineCartesianLayer.AreaFill.single(Fill(primary.copy(alpha = 0.3f))) },
                interpolator = StepInterpolator,
            )
        } else {
            LineCartesianLayer.rememberLine(
                fill = remember(primary) { LineCartesianLayer.LineFill.single(Fill(primary)) },
            )
        }
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = style.yFormatter,
                    itemPlacer = remember(style.yStep) {
                        VerticalAxis.ItemPlacer.step(step = { style.yStep })
                    },
                ),
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
                .semantics { contentDescription = style.description },
        )
    }
}

/**
 * Holds each sample's value back to the previous sample: a bucket's state (including events
 * counted since the previous sample) covers the interval leading up to it.
 */
private object StepInterpolator : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange,
    ) {
        for (index in visibleIndexRange) {
            val point = points[index]
            if (index == visibleIndexRange.first) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(points[index - 1].x, point.y)
                path.lineTo(point.x, point.y)
            }
        }
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

internal val CHART_HEIGHT = 220.dp
private const val LABEL_HOURS = 6
