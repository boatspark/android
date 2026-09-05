package com.boatspark.app.core.model

import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class DisplayFormattingTest {
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUp() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousTimeZone)
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
}
