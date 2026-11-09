package com.boatspark.app.core.model

import com.boatspark.app.core.network.BoatSparkJson
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class GpioHistoryTest {
    private fun sample(time: String, volts: Int, shore: Int, shoreCycles: Int, bilge: Int, bilgeCycles: Int) = """
        {"published_at": "$time", "values": {
          "battery1_voltage": $volts, "battery2_voltage": 1290,
          "shore_power_status": $shore, "shore_power_cycle": $shoreCycles,
          "bilge_pump_status": $bilge, "bilge_pump_cycle": $bilgeCycles,
          "engine_status": 0, "engine_cycle": 0}}
    """

    private val history = BoatSparkJson.decodeFromString<BoatHistory>(
        """
        {
          "boat_id": 1,
          "window_start": "2026-02-01T00:00:00Z",
          "window_end": "2026-02-03T00:00:00Z",
          "interval_minutes": 15,
          "gpio": [
            ${sample("2026-02-01T00:10:00Z", 1380, 1, 4, 0, 10)},
            ${sample("2026-02-01T00:25:00Z", 1375, 1, 5, 0, 12)},
            ${sample("2026-02-01T00:40:00Z", 1370, 0, 5, 1, 12)},
            ${sample("2026-02-01T00:55:00Z", 1365, 1, 1, 0, 0)}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `voltage charts the sampled volts`() {
        val chart = gpioChart(history, GpioMetric.Battery1)!!
        assertEquals(
            listOf(listOf(ChartPoint(0, 13.8), ChartPoint(1, 13.75), ChartPoint(2, 13.7), ChartPoint(3, 13.65))),
            chart.segments,
        )
    }

    @Test
    fun `counter increases mark short events missed by sampling`() {
        // Bilge pump ran twice before the second sample, though both samples read off.
        assertEquals(
            listOf(0.0, 1.0, 1.0, 0.0),
            gpioChart(history, GpioMetric.BilgePump)!!.segments.single().map { it.value },
        )
        // Shore power's counter counts losses, so an increase marks the interval as off.
        assertEquals(
            listOf(1.0, 0.0, 0.0, 0.0),
            gpioChart(history, GpioMetric.ShorePower)!!.segments.single().map { it.value },
        )
    }

    @Test
    fun `event count adds increases and restarts from zero after a reboot`() {
        assertEquals(2, gpioEventCount(history, GpioMetric.BilgePump))
        assertEquals(2, gpioEventCount(history, GpioMetric.ShorePower))
        assertEquals(0, gpioEventCount(history, GpioMetric.Engine))
        assertNull(gpioEventCount(history, GpioMetric.Battery1))
    }
}
