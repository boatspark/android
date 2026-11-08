package com.boatspark.app.core.model

import com.boatspark.app.core.settings.TemperatureUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

fun formatDecimal(value: Double, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

fun formatTemperature(celsius: Double, unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.Fahrenheit -> "${formatDecimal(celsius * 9 / 5 + 32)} °F"
    TemperatureUnit.Celsius -> "${formatDecimal(celsius)} °C"
}

fun formatTimestamp(value: String?): String {
    if (value == null) return "Unavailable"
    return try {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(normalizeTimestamp(value)))
    } catch (_: DateTimeParseException) {
        value
    }
}

private fun normalizeTimestamp(value: String): String =
    if (value.endsWith("Z") || OFFSET_SUFFIX.containsMatchIn(value)) value else "${value}Z"

private val OFFSET_SUFFIX = Regex("[+-]\\d{2}:\\d{2}$")

fun binaryStatus(value: Int): String = when (value) {
    0 -> "Off"
    1 -> "On"
    else -> "Unknown ($value)"
}

fun powerSource(value: Int): String = when (value) {
    0 -> "Unknown"
    1 -> "VIN"
    2 -> "USB host"
    3 -> "USB adapter"
    4 -> "USB OTG"
    5 -> "Battery"
    else -> "Unknown ($value)"
}
