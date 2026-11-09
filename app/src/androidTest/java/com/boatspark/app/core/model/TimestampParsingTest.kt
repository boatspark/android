package com.boatspark.app.core.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the device because the platform java.time differs from the JVM's below API 34. */
@RunWith(AndroidJUnit4::class)
class TimestampParsingTest {
    @Test
    fun parsesZuluOffsetAndZonelessTimestamps() {
        val expected = Instant.parse("2026-02-03T00:05:00Z")
        assertEquals(expected, parseTimestamp("2026-02-03T00:05:00Z"))
        assertEquals(expected, parseTimestamp("2026-02-03T00:05:00+00:00"))
        assertEquals(expected, parseTimestamp("2026-02-03T01:05:00+01:00"))
        assertEquals(expected, parseTimestamp("2026-02-03T00:05:00"))
        assertEquals(
            Instant.parse("2026-02-03T00:05:00.123456Z"),
            parseTimestamp("2026-02-03T00:05:00.123456+00:00"),
        )
    }
}
