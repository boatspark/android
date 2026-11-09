package com.boatspark.app.core.model

import com.boatspark.app.core.settings.TemperatureUnit
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class DisplayFormattingTest {
    private lateinit var previousTimeZone: TimeZone
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousTimeZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousTimeZone)
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `UTC and offset timestamps are formatted as instants`() {
        assertEquals("Feb 1, 2026 12:00", formatTimestamp("2026-02-01T12:00:00Z"))
        assertEquals("Feb 1, 2026 12:00", formatTimestamp("2026-02-01T07:00:00-05:00"))
    }

    @Test
    fun `timezone-free API timestamp is treated as UTC`() {
        assertEquals("Feb 1, 2026 12:00", formatTimestamp("2026-02-01T12:00:00"))
    }

    @Test
    fun `temperatures are converted to the selected unit with one decimal`() {
        assertEquals("72.5 °F", formatTemperature(22.5, TemperatureUnit.Fahrenheit))
        assertEquals("14.0 °F", formatTemperature(-10.0, TemperatureUnit.Fahrenheit))
        assertEquals("22.5 °C", formatTemperature(22.5, TemperatureUnit.Celsius))
        assertEquals("22.6 °C", formatTemperature(22.56, TemperatureUnit.Celsius))
    }

    @Test
    fun `battery percentage maps to thirds`() {
        assertEquals(3, batteryThirds(100))
        assertEquals(3, batteryThirds(67))
        assertEquals(2, batteryThirds(66))
        assertEquals(2, batteryThirds(34))
        assertEquals(1, batteryThirds(33))
        assertEquals(1, batteryThirds(0))
        assertEquals(null, batteryThirds(-1))
        assertEquals(null, batteryThirds(101))
    }
}
