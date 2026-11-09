package com.boatspark.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: Long,
    val email: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class Boat(
    val id: Long,
    val name: String,
    @SerialName("device_identifier") val deviceIdentifier: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BoatList(val boats: List<Boat>)

@Serializable
data class GpsData(
    val timestamp: String,
    val fix: Int,
    val satellites: Int,
    val latitude: Long,
    val longitude: Long,
    @SerialName("speed_over_ground") val speedOverGround: Int,
    @SerialName("course_over_ground") val courseOverGround: Int,
    val altitude: Int,
    val pdop: Int,
    @SerialName("fix_quality") val fixQuality: Int,
    @SerialName("fix_quality_3d") val fixQuality3d: Int,
) {
    val latitudeDegrees: Double get() = latitude / 10_000_000.0
    val longitudeDegrees: Double get() = longitude / 10_000_000.0
    val speedKnots: Double get() = speedOverGround / 100.0
    val pdopValue: Double get() = pdop / 100.0
}

@Serializable
data class GpioData(
    @SerialName("battery1_voltage") val battery1Voltage: Int,
    @SerialName("battery2_voltage") val battery2Voltage: Int,
    @SerialName("shore_power_status") val shorePowerStatus: Int,
    @SerialName("shore_power_cycle") val shorePowerCycle: Int,
    @SerialName("bilge_pump_status") val bilgePumpStatus: Int,
    @SerialName("bilge_pump_cycle") val bilgePumpCycle: Int,
    @SerialName("engine_status") val engineStatus: Int,
    @SerialName("engine_cycle") val engineCycle: Int,
) {
    val battery1Volts: Double get() = battery1Voltage / 100.0
    val battery2Volts: Double get() = battery2Voltage / 100.0
}

@Serializable
data class SystemData(
    @SerialName("power_source") val powerSource: Int,
    @SerialName("battery_soc") val batterySoc: Int,
    @SerialName("free_memory") val freeMemory: Long,
    @SerialName("signal_strength") val signalStrength: Int,
    @SerialName("signal_quality") val signalQuality: Int,
) {
    val batteryPercent: Double? get() = batterySoc.takeUnless { it == -1 }?.div(10.0)
    val signalStrengthPercent: Double get() = signalStrength / 10.0
    val signalQualityPercent: Double get() = signalQuality / 10.0
}

@Serializable
data class BeaconReading(
    @SerialName("beacon_id") val beaconId: Long,
    @SerialName("beacon_name") val beaconName: String,
    val rssi: Int,
    val temperature: Int,
    val humidity: Int,
    @SerialName("battery_percentage") val batteryPercentage: Int,
    @SerialName("battery_voltage") val batteryVoltage: Int,
) {
    val temperatureCelsius: Double get() = temperature / 100.0
    val humidityPercent: Double get() = humidity / 100.0
}

@Serializable
data class LastGps(
    val gps: GpsData,
    @SerialName("published_at") val publishedAt: String,
)

@Serializable
data class LastBeaconReading(
    val beacon: BeaconReading,
    @SerialName("published_at") val publishedAt: String,
)

@Serializable
data class BoatStatus(
    @SerialName("boat_id") val boatId: Long,
    @SerialName("boat_name") val boatName: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("firmware_version") val firmwareVersion: Int? = null,
    val gps: GpsData? = null,
    @SerialName("gps_no_fix") val gpsNoFix: Boolean = false,
    @SerialName("last_gps") val lastGps: LastGps? = null,
    val gpio: GpioData? = null,
    val system: SystemData? = null,
    val beacons: List<BeaconReading> = emptyList(),
    @SerialName("missing_beacons") val missingBeacons: List<LastBeaconReading> = emptyList(),
)

@Serializable
data class Alert(
    val id: Long,
    @SerialName("alert_type") val alertType: String,
    @SerialName("alert_description") val alertDescription: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BoatAlerts(
    @SerialName("boat_id") val boatId: Long,
    @SerialName("boat_name") val boatName: String,
    val alerts: List<Alert>,
)

@Serializable
data class BeaconHistoryValues(
    val temperature: Int? = null,
) {
    val temperatureCelsius: Double? get() = temperature?.let { it / 100.0 }
}

@Serializable
data class BeaconHistorySample(
    @SerialName("published_at") val publishedAt: String,
    val values: BeaconHistoryValues,
)

@Serializable
data class BeaconHistory(
    @SerialName("beacon_id") val beaconId: Long,
    @SerialName("beacon_name") val beaconName: String,
    val readings: List<BeaconHistorySample> = emptyList(),
)

@Serializable
data class BoatHistory(
    @SerialName("boat_id") val boatId: Long,
    @SerialName("window_start") val windowStart: String,
    @SerialName("window_end") val windowEnd: String,
    @SerialName("interval_minutes") val intervalMinutes: Int = 15,
    val gpio: List<GpioHistorySample> = emptyList(),
    val beacons: List<BeaconHistory> = emptyList(),
)

@Serializable
data class GpioHistorySample(
    @SerialName("published_at") val publishedAt: String,
    val values: GpioData,
)

@Serializable
data class ApiError(val detail: String)

