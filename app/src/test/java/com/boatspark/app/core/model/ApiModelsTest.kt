package com.boatspark.app.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import org.junit.Test

class ApiModelsTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `status schema includes last GPS and missing beacons`() {
        val status = json.decodeFromString<BoatStatus>(
            """
            {
              "boat_id": 1,
              "boat_name": "Sea Breeze",
              "published_at": "2026-02-01T12:00:00Z",
              "firmware_version": 2,
              "gps": null,
              "gps_no_fix": true,
              "last_gps": {
                "gps": {
                  "timestamp": "2026-02-01T09:00:00Z",
                  "fix": 1,
                  "satellites": 8,
                  "latitude": 412345678,
                  "longitude": -721234567,
                  "speed_over_ground": 125,
                  "course_over_ground": 180,
                  "altitude": 5,
                  "pdop": 150,
                  "fix_quality": 1,
                  "fix_quality_3d": 3
                },
                "published_at": "2026-02-01T09:00:00Z"
              },
              "gpio": null,
              "system": null,
              "beacons": [],
              "missing_beacons": [{
                "beacon": {
                  "beacon_id": 3,
                  "beacon_name": "Cabin",
                  "rssi": -65,
                  "temperature": 2250,
                  "humidity": 5500,
                  "battery_percentage": 95,
                  "battery_voltage": 3000
                },
                "published_at": "2026-02-01T10:00:00Z"
              }]
            }
            """.trimIndent(),
        )

        assertNull(status.gps)
        assertEquals(true, status.gpsNoFix)
        assertEquals(41.2345678, status.lastGps?.gps?.latitudeDegrees)
        assertEquals(-72.1234567, status.lastGps?.gps?.longitudeDegrees)
        assertEquals(1.25, status.lastGps?.gps?.speedKnots)
        assertEquals(1.5, status.lastGps?.gps?.pdopValue)
        assertEquals(22.5, status.missingBeacons.single().beacon.temperatureCelsius)
        assertEquals(55.0, status.missingBeacons.single().beacon.humidityPercent)
    }

    @Test
    fun `telemetry fixed point values use documented units`() {
        val gpio = GpioData(1280, 1265, 1, 5, 0, 2, 0, 10)
        val system = SystemData(1, 850, 100_000, 750, 600)
        val disconnected = system.copy(batterySoc = -1)

        assertEquals(12.8, gpio.battery1Volts)
        assertEquals(12.65, gpio.battery2Volts)
        assertEquals(85.0, system.batteryPercent)
        assertEquals(75.0, system.signalStrengthPercent)
        assertNull(disconnected.batteryPercent)
    }

    @Test
    fun `unknown enum values remain visible`() {
        assertEquals("Unknown (9)", binaryStatus(9))
        assertEquals("Unknown (99)", powerSource(99))
    }
}

