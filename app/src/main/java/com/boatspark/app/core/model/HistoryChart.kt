package com.boatspark.app.core.model

import com.boatspark.app.core.settings.TemperatureUnit
import java.time.Instant

data class ChartPoint(val bucket: Int, val value: Double)

data class HistoryChart(
    /** Start of bucket 0, aligned to the server's UTC sampling interval. */
    val origin: Instant,
    val intervalMinutes: Int,
    val bucketCount: Int,
    val segments: List<List<ChartPoint>>,
)

fun temperatureChart(
    history: BoatHistory,
    beaconId: Long,
    unit: TemperatureUnit,
    maxBucketGap: Int = 2,
): HistoryChart? {
    val samples = history.beacons.firstOrNull { it.beaconId == beaconId }?.readings.orEmpty()
        .mapNotNull { sample ->
            val time = parseTimestamp(sample.publishedAt) ?: return@mapNotNull null
            val celsius = sample.values.temperatureCelsius ?: return@mapNotNull null
            time to convertTemperature(celsius, unit)
        }
    return historyChart(history, samples, maxBucketGap)
}

/**
 * Converts timed values into chart points keyed by bucket index from the interval-aligned
 * window start.
 * Integer x values keep the chart's x step at one bucket even though sample times are irregular.
 * Points more than [maxBucketGap] buckets apart start a new segment so gaps aren't bridged.
 */
internal fun historyChart(
    history: BoatHistory,
    samples: List<Pair<Instant, Double>>,
    maxBucketGap: Int,
): HistoryChart? {
    val windowStart = parseTimestamp(history.windowStart) ?: return null
    val windowEnd = parseTimestamp(history.windowEnd) ?: return null
    val interval = history.intervalMinutes.takeIf { it > 0 } ?: return null
    if (windowEnd <= windowStart) return null
    val intervalSeconds = interval * 60L
    val origin = Instant.ofEpochSecond(Math.floorDiv(windowStart.epochSecond, intervalSeconds) * intervalSeconds)
    val bucketCount = (Math.floorDiv(windowEnd.epochSecond - origin.epochSecond, intervalSeconds) + 1).toInt()

    val points = samples
        .filter { (time, _) -> time >= windowStart && time <= windowEnd }
        .sortedBy { it.first }
        .map { (time, value) ->
            ChartPoint(Math.floorDiv(time.epochSecond - origin.epochSecond, intervalSeconds).toInt(), value)
        }
        // Keep the latest sample in each bucket, matching the server's sampling.
        .asReversed()
        .distinctBy { it.bucket }
        .asReversed()

    val segments = mutableListOf<MutableList<ChartPoint>>()
    points.forEach { point ->
        val current = segments.lastOrNull()
        if (current == null || point.bucket - current.last().bucket > maxBucketGap) {
            segments += mutableListOf(point)
        } else {
            current += point
        }
    }
    return HistoryChart(origin, interval, bucketCount, segments)
}
