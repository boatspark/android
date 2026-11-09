package com.boatspark.app.core.model

import com.boatspark.app.core.settings.TemperatureUnit
import java.time.Instant

data class ChartPoint(val bucket: Int, val temperature: Double)

data class TemperatureChart(
    /** Start of bucket 0, aligned to the server's UTC sampling interval. */
    val origin: Instant,
    val intervalMinutes: Int,
    val bucketCount: Int,
    val segments: List<List<ChartPoint>>,
)

/**
 * Converts a sensor's history into chart points keyed by bucket index from the interval-aligned
 * window start.
 * Integer x values keep the chart's x step at one bucket even though sample times are irregular.
 * Points more than [maxBucketGap] buckets apart start a new segment so gaps aren't bridged.
 */
fun temperatureChart(
    history: BoatHistory,
    beaconId: Long,
    unit: TemperatureUnit,
    maxBucketGap: Int = 2,
): TemperatureChart? {
    val windowStart = parseTimestamp(history.windowStart) ?: return null
    val windowEnd = parseTimestamp(history.windowEnd) ?: return null
    val interval = history.intervalMinutes.takeIf { it > 0 } ?: return null
    val intervalSeconds = interval * 60L
    val origin = Instant.ofEpochSecond(Math.floorDiv(windowStart.epochSecond, intervalSeconds) * intervalSeconds)
    val bucketCount = (Math.floorDiv(windowEnd.epochSecond - origin.epochSecond, intervalSeconds) + 1).toInt()
    if (windowEnd <= windowStart) return null

    val points = history.beacons.firstOrNull { it.beaconId == beaconId }?.readings.orEmpty()
        .mapNotNull { sample ->
            val time = parseTimestamp(sample.publishedAt) ?: return@mapNotNull null
            val celsius = sample.values.temperatureCelsius ?: return@mapNotNull null
            if (time < windowStart || time > windowEnd) return@mapNotNull null
            val bucket = Math.floorDiv(time.epochSecond - origin.epochSecond, intervalSeconds).toInt()
            ChartPoint(bucket, convertTemperature(celsius, unit))
        }
        .sortedBy { it.bucket }
        .distinctBy { it.bucket }

    val segments = mutableListOf<MutableList<ChartPoint>>()
    points.forEach { point ->
        val current = segments.lastOrNull()
        if (current == null || point.bucket - current.last().bucket > maxBucketGap) {
            segments += mutableListOf(point)
        } else {
            current += point
        }
    }
    return TemperatureChart(origin, interval, bucketCount, segments)
}
