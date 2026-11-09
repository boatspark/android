package com.boatspark.app.core.model

import com.boatspark.app.core.network.BoatSparkJson
import com.boatspark.app.core.settings.TemperatureUnit
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TemperatureHistoryTest {
    private val history = BoatSparkJson.decodeFromString<BoatHistory>(
        """
        {
          "boat_id": 1,
          "boat_name": "Sea Breeze",
          "window_start": "2026-02-01T00:05:00Z",
          "window_end": "2026-02-03T00:05:00Z",
          "interval_minutes": 15,
          "gpio": [],
          "beacons": [
            {"beacon_id": 7, "beacon_name": "Other", "readings": [
              {"published_at": "2026-02-01T00:05:00Z", "values": {"temperature": 1000}}
            ]},
            {"beacon_id": 3, "beacon_name": "Cabin", "readings": [
              {"published_at": "2026-02-01T00:20:00Z", "values": {"rssi": -60, "temperature": 2000, "humidity": 5000, "battery_percentage": 90, "battery_voltage": 3000}},
              {"published_at": "2026-02-01T00:04:59Z", "values": {"temperature": 1500}},
              {"published_at": "2026-02-01T00:07:00", "values": {"temperature": 2250}},
              {"published_at": "2026-02-01T00:35:00Z", "values": {"humidity": 5000}},
              {"published_at": "2026-02-01T02:00:00Z", "values": {"temperature": 2500}},
              {"published_at": "2026-02-03T00:05:00+00:00", "values": {"temperature": 3000}},
              {"published_at": "2026-02-03T00:05:01Z", "values": {"temperature": 3100}}
            ]}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `history parses with beacon ids and optional values`() {
        assertEquals(listOf(7L, 3L), history.beacons.map { it.beaconId })
        assertEquals(22.5, history.beacons[1].readings[2].values.temperatureCelsius)
        assertNull(history.beacons[1].readings[3].values.temperatureCelsius)
    }

    @Test
    fun `chart aligns buckets, drops out-of-window samples, converts units and splits at gaps`() {
        val chart = temperatureChart(history, beaconId = 3, unit = TemperatureUnit.Fahrenheit)!!

        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), chart.origin)
        assertEquals(193, chart.bucketCount)
        assertEquals(
            listOf(
                listOf(ChartPoint(0, 72.5), ChartPoint(1, 68.0)),
                listOf(ChartPoint(8, 77.0)),
                listOf(ChartPoint(192, 86.0)),
            ),
            chart.segments,
        )
    }

    @Test
    fun `unknown sensor has no segments`() {
        assertEquals(emptyList(), temperatureChart(history, beaconId = 99, unit = TemperatureUnit.Celsius)!!.segments)
    }
}
