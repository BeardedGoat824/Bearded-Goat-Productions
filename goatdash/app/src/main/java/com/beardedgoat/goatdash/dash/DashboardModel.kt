package com.beardedgoat.goatdash.dash

import org.json.JSONArray
import org.json.JSONObject

enum class GaugeStyle(val label: String) {
    DIAL("Dial"),
    BAR("Bar"),
    DIGITAL("Digital"),
    GRAPH("Graph"),
}

/** Gauge colours (ARGB). The first one is the default accent. */
object GaugeColors {
    val palette = listOf(
        0xFFFF8A1F.toInt(), // goat orange
        0xFF38BDF8.toInt(), // sky blue
        0xFFA3E635.toInt(), // lime
        0xFFF43F5E.toInt(), // red
        0xFFC084FC.toInt(), // purple
        0xFFFACC15.toInt(), // yellow
        0xFFF1F5F9.toInt(), // white
    )
    const val WARNING = 0xFFEF4444.toInt()
}

/**
 * One gauge slot. [warnAbove] is in metric base units (so switching mph/km/h never moves
 * the threshold); null means no warning.
 */
data class Gauge(
    val pidId: String,
    val style: GaugeStyle = GaugeStyle.DIAL,
    val color: Int = GaugeColors.palette[0],
    val warnAbove: Double? = null,
)

data class DashPage(
    val name: String,
    val columns: Int,
    val rows: Int,
    val gauges: List<Gauge>,
) {
    val slots get() = columns * rows

    /** Copy with a new grid size, keeping existing gauges and filling new slots. */
    fun resized(columns: Int, rows: Int): DashPage {
        val c = columns.coerceIn(1, MAX_COLUMNS)
        val r = rows.coerceIn(1, MAX_ROWS)
        val filled = (0 until c * r).map { i -> gauges.getOrNull(i) ?: Gauge(FILLER_PIDS[i % FILLER_PIDS.size], GaugeStyle.DIGITAL) }
        return copy(columns = c, rows = r, gauges = filled)
    }

    companion object {
        const val MAX_COLUMNS = 4
        const val MAX_ROWS = 3
        private val FILLER_PIDS = listOf("SPEED", "RPM", "COOLANT", "BATTERY", "THROTTLE", "LOAD")
    }
}

data class DashboardConfig(
    val pages: List<DashPage>,
    val imperial: Boolean = true,
) {
    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("imperial", imperial)
        put("pages", JSONArray().apply {
            pages.forEach { page ->
                put(JSONObject().apply {
                    put("name", page.name)
                    put("columns", page.columns)
                    put("rows", page.rows)
                    put("gauges", JSONArray().apply {
                        page.gauges.forEach { g ->
                            put(JSONObject().apply {
                                put("pid", g.pidId)
                                put("style", g.style.name)
                                put("color", g.color)
                                g.warnAbove?.let { put("warnAbove", it) }
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): DashboardConfig {
            val root = JSONObject(json)
            val pagesJson = root.getJSONArray("pages")
            val pages = (0 until pagesJson.length()).map { i ->
                val p = pagesJson.getJSONObject(i)
                val gaugesJson = p.getJSONArray("gauges")
                val gauges = (0 until gaugesJson.length()).map { j ->
                    val g = gaugesJson.getJSONObject(j)
                    Gauge(
                        pidId = g.getString("pid"),
                        style = runCatching { GaugeStyle.valueOf(g.getString("style")) }.getOrDefault(GaugeStyle.DIAL),
                        color = g.optInt("color", GaugeColors.palette[0]),
                        warnAbove = if (g.has("warnAbove")) g.getDouble("warnAbove") else null,
                    )
                }
                DashPage(p.getString("name"), p.getInt("columns"), p.getInt("rows"), gauges).let { it.resized(it.columns, it.rows) }
            }
            return DashboardConfig(pages.ifEmpty { default().pages }, root.optBoolean("imperial", true))
        }

        fun default(): DashboardConfig {
            val orange = GaugeColors.palette[0]
            val blue = GaugeColors.palette[1]
            val lime = GaugeColors.palette[2]
            return DashboardConfig(
                pages = listOf(
                    DashPage(
                        "Main", 3, 2, listOf(
                            Gauge("RPM", GaugeStyle.DIAL, orange, 6000.0),
                            Gauge("SPEED", GaugeStyle.DIAL, blue),
                            Gauge("COOLANT", GaugeStyle.DIAL, lime, 110.0),
                            Gauge("THROTTLE", GaugeStyle.BAR, orange),
                            Gauge("BATTERY", GaugeStyle.DIGITAL, blue, 15.0),
                            Gauge("LOAD", GaugeStyle.BAR, lime),
                        ),
                    ),
                    DashPage(
                        "Engine", 2, 2, listOf(
                            Gauge("MAF", GaugeStyle.GRAPH, blue),
                            Gauge("INTAKE_TEMP", GaugeStyle.DIGITAL, lime),
                            Gauge("TIMING", GaugeStyle.GRAPH, orange),
                            Gauge("STFT1", GaugeStyle.BAR, GaugeColors.palette[4]),
                        ),
                    ),
                    DashPage(
                        "Cruise", 2, 1, listOf(
                            Gauge("SPEED", GaugeStyle.DIGITAL, GaugeColors.palette[6]),
                            Gauge("FUEL_LEVEL", GaugeStyle.BAR, orange),
                        ),
                    ),
                ),
            )
        }
    }
}
