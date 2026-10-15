package com.beardedgoat.goatdash.obd

import kotlin.math.roundToInt

/** What a reading measures. Values are always stored in the metric base unit shown here. */
enum class UnitKind(val metric: String, val imperial: String) {
    PERCENT("%", "%"),
    RPM("rpm", "rpm"),
    SPEED("km/h", "mph"),
    TEMP("°C", "°F"),
    PRESSURE("kPa", "psi"),
    VOLT("V", "V"),
    AIRFLOW("g/s", "g/s"),
    FUEL_RATE("L/h", "gal/h"),
    DEGREES("°", "°");

    fun label(imperial: Boolean) = if (imperial) this.imperial else metric

    /** Converts a metric base value to what the driver wants to see. */
    fun toDisplay(value: Double, imperial: Boolean): Double = if (!imperial) value else when (this) {
        SPEED -> value * 0.621371
        TEMP -> value * 9.0 / 5.0 + 32.0
        PRESSURE -> value * 0.145038
        FUEL_RATE -> value * 0.264172
        else -> value
    }

    fun fromDisplay(value: Double, imperial: Boolean): Double = if (!imperial) value else when (this) {
        SPEED -> value / 0.621371
        TEMP -> (value - 32.0) * 5.0 / 9.0
        PRESSURE -> value / 0.145038
        FUEL_RATE -> value / 0.264172
        else -> value
    }
}

/**
 * One live reading the dashboard can show.
 *
 * [command] is what gets sent to the ELM327 (e.g. "010C"). [min]/[max] are the gauge's
 * default scale in metric units, and [decode] turns the response data bytes into a value.
 */
class Pid(
    val id: String,
    val name: String,
    val shortName: String,
    val command: String,
    val unit: UnitKind,
    val min: Double,
    val max: Double,
    val decimals: Int = 0,
    val defaultWarn: Double? = null,
    val decode: (IntArray) -> Double?,
) {
    /** Mode 01 PID number, or null for adapter commands like ATRV. */
    val mode01Pid: Int? = if (command.startsWith("01") && command.length == 4) command.substring(2).toInt(16) else null

    fun format(value: Double?, imperial: Boolean): String {
        if (value == null) return "--"
        val v = unit.toDisplay(value, imperial)
        return if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(v)
    }
}

private fun word(b: IntArray) = if (b.size >= 2) b[0] * 256 + b[1] else null
private fun byte(b: IntArray) = b.getOrNull(0)

object Pids {
    val all: List<Pid> = listOf(
        Pid("RPM", "Engine RPM", "RPM", "010C", UnitKind.RPM, 0.0, 8000.0, defaultWarn = 6000.0) { b -> word(b)?.let { it / 4.0 } },
        Pid("SPEED", "Vehicle speed", "Speed", "010D", UnitKind.SPEED, 0.0, 220.0) { b -> byte(b)?.toDouble() },
        Pid("COOLANT", "Coolant temperature", "Coolant", "0105", UnitKind.TEMP, 40.0, 130.0, defaultWarn = 110.0) { b -> byte(b)?.let { it - 40.0 } },
        Pid("INTAKE_TEMP", "Intake air temperature", "Intake", "010F", UnitKind.TEMP, -20.0, 80.0) { b -> byte(b)?.let { it - 40.0 } },
        Pid("OIL_TEMP", "Engine oil temperature", "Oil temp", "015C", UnitKind.TEMP, 40.0, 150.0, defaultWarn = 130.0) { b -> byte(b)?.let { it - 40.0 } },
        Pid("AMBIENT", "Outside air temperature", "Outside", "0146", UnitKind.TEMP, -30.0, 50.0) { b -> byte(b)?.let { it - 40.0 } },
        Pid("THROTTLE", "Throttle position", "Throttle", "0111", UnitKind.PERCENT, 0.0, 100.0) { b -> byte(b)?.let { it * 100.0 / 255.0 } },
        Pid("LOAD", "Engine load", "Load", "0104", UnitKind.PERCENT, 0.0, 100.0) { b -> byte(b)?.let { it * 100.0 / 255.0 } },
        Pid("FUEL_LEVEL", "Fuel tank level", "Fuel", "012F", UnitKind.PERCENT, 0.0, 100.0) { b -> byte(b)?.let { it * 100.0 / 255.0 } },
        Pid("MAP", "Intake manifold pressure", "MAP", "010B", UnitKind.PRESSURE, 0.0, 250.0) { b -> byte(b)?.toDouble() },
        Pid("BARO", "Barometric pressure", "Baro", "0133", UnitKind.PRESSURE, 70.0, 110.0) { b -> byte(b)?.toDouble() },
        Pid("MAF", "Mass air flow", "MAF", "0110", UnitKind.AIRFLOW, 0.0, 200.0, decimals = 1) { b -> word(b)?.let { it / 100.0 } },
        Pid("TIMING", "Timing advance", "Timing", "010E", UnitKind.DEGREES, -10.0, 50.0, decimals = 1) { b -> byte(b)?.let { it / 2.0 - 64.0 } },
        Pid("STFT1", "Short-term fuel trim (bank 1)", "STFT", "0106", UnitKind.PERCENT, -25.0, 25.0, decimals = 1) { b -> byte(b)?.let { it * 100.0 / 128.0 - 100.0 } },
        Pid("LTFT1", "Long-term fuel trim (bank 1)", "LTFT", "0107", UnitKind.PERCENT, -25.0, 25.0, decimals = 1) { b -> byte(b)?.let { it * 100.0 / 128.0 - 100.0 } },
        Pid("FUEL_RATE", "Fuel consumption rate", "Fuel rate", "015E", UnitKind.FUEL_RATE, 0.0, 30.0, decimals = 1) { b -> word(b)?.let { it / 20.0 } },
        Pid("ECU_VOLT", "Control module voltage", "ECU volts", "0142", UnitKind.VOLT, 10.0, 16.0, decimals = 1) { b -> word(b)?.let { it / 1000.0 } },
        // Read by the adapter itself from the OBD port's 12 V pin, so it works on every car.
        Pid("BATTERY", "Battery voltage (adapter)", "Battery", "ATRV", UnitKind.VOLT, 10.0, 16.0, decimals = 1, defaultWarn = 15.0) { null },
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): Pid? = byId[id]
}
