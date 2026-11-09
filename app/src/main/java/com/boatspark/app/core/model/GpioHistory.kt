package com.boatspark.app.core.model

enum class GpioMetric(val routeKey: String, val title: String, val isBinary: Boolean) {
    Battery1("battery1", "Battery 1", false),
    Battery2("battery2", "Battery 2", false),
    ShorePower("shore-power", "Shore power", true),
    BilgePump("bilge-pump", "Bilge pump", true),
    Engine("engine", "Engine", true),
    ;

    /** The value as charted: volts, or 1 for on and 0 for off. */
    fun value(gpio: GpioData): Double = when (this) {
        Battery1 -> gpio.battery1Volts
        Battery2 -> gpio.battery2Volts
        ShorePower -> gpio.shorePowerStatus.toDouble()
        BilgePump -> gpio.bilgePumpStatus.toDouble()
        Engine -> gpio.engineStatus.toDouble()
    }

    fun display(gpio: GpioData): String =
        if (isBinary) binaryStatus(value(gpio).toInt()) else "${formatDecimal(value(gpio), 2)} V"

    /**
     * The firmware's cumulative event counter since boot. It counts turn-ons for the engine and
     * bilge pump, but losses for shore power.
     */
    internal fun cycles(gpio: GpioData): Int? = when (this) {
        ShorePower -> gpio.shorePowerCycle
        BilgePump -> gpio.bilgePumpCycle
        Engine -> gpio.engineCycle
        else -> null
    }

    /** The state that [cycles] counts entries into. */
    internal val eventValue: Double get() = if (this == ShorePower) 0.0 else 1.0

    companion object {
        fun fromRouteKey(key: String?): GpioMetric? = entries.firstOrNull { it.routeKey == key }
    }
}

/**
 * Charts a GPIO value's history. On/off states also use the event counters, so a short run
 * between samples (for example the bilge pump) still marks its interval rather than being missed.
 */
fun gpioChart(history: BoatHistory, metric: GpioMetric, maxBucketGap: Int = 2): HistoryChart? {
    val samples = history.gpio.mapNotNull { sample -> parseTimestamp(sample.publishedAt)?.let { it to sample.values } }
        .sortedBy { it.first }
    var previous: GpioData? = null
    val values = samples.map { (time, gpio) ->
        val value = if (metric.isBinary && eventsSince(metric, previous, gpio) > 0) metric.eventValue else metric.value(gpio)
        previous = gpio
        time to value
    }
    return historyChart(history, values, maxBucketGap)
}

/**
 * Counts events (engine or bilge pump turned on, shore power lost) during the history window,
 * or null for values without an event counter.
 */
fun gpioEventCount(history: BoatHistory, metric: GpioMetric): Int? {
    if (!metric.isBinary) return null
    val windowStart = parseTimestamp(history.windowStart)
    val windowEnd = parseTimestamp(history.windowEnd)
    val samples = history.gpio.mapNotNull { sample ->
        val time = parseTimestamp(sample.publishedAt) ?: return@mapNotNull null
        if ((windowStart != null && time < windowStart) || (windowEnd != null && time > windowEnd)) return@mapNotNull null
        time to sample.values
    }.sortedBy { it.first }.map { it.second }
    return samples.zipWithNext { before, after -> eventsSince(metric, before, after) }.sum()
}

private fun eventsSince(metric: GpioMetric, before: GpioData?, after: GpioData): Int {
    val previous = before?.let(metric::cycles) ?: return 0
    val current = metric.cycles(after) ?: return 0
    // A lower count means the device restarted and counted from zero since.
    return if (current >= previous) current - previous else current
}
