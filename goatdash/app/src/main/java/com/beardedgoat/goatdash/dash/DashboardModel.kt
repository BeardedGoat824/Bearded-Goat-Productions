package com.beardedgoat.goatdash.dash

import com.beardedgoat.goatdash.obd.LiveData
import org.json.JSONArray
import org.json.JSONObject

enum class GaugeStyle(val label: String) {
    DIAL("Dial"),
    BAR("Bar"),
    DIGITAL("Digital"),
    GRAPH("Graph"),
}

/** Buttons a music tile (and the car's button bar) can show, in the order they appear. */
enum class MusicButton(val label: String) {
    PREVIOUS("Previous"),
    BACK_15("Back 15s"),
    PLAY_PAUSE("Play/Pause"),
    FORWARD_30("Forward 30s"),
    NEXT("Next"),
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

    /** Bright colours offered for the shift light, alongside "Auto". */
    val shiftPalette = listOf(0xFF00E5FF.toInt(), 0xFF2979FF.toInt(), 0xFFFF1744.toInt(), 0xFFFFEA00.toInt(), 0xFF76FF03.toInt(), 0xFFFFFFFF.toInt())

    /**
     * The colour opposite [color] on the colour wheel, at full brightness, so a flash always
     * stands out from the gauge's normal colour. Greys and white get electric cyan.
     */
    fun contrasting(color: Int): Int {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (max == 0f || delta / max < 0.25f) return 0xFF00E5FF.toInt()
        val hue = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return fullColor((hue + 180f + 360f) % 360f)
    }

    /** Fully saturated, full-brightness colour for a hue in degrees. */
    private fun fullColor(hue: Float): Int {
        val x = 1f - kotlin.math.abs((hue / 60f) % 2f - 1f)
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(1f, x, 0f)
            1 -> Triple(x, 1f, 0f)
            2 -> Triple(0f, 1f, x)
            3 -> Triple(0f, x, 1f)
            4 -> Triple(x, 0f, 1f)
            else -> Triple(1f, 0f, x)
        }
        fun c(v: Float) = Math.round(v * 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}

/**
 * One gauge slot. [warnAbove] is in metric base units (so switching mph/km/h never moves
 * the threshold); null means no warning.
 *
 * [shiftAt] turns the gauge into a shift light: at or above that reading the whole tile
 * flashes [shiftColor] (null = automatically pick a colour that contrasts with [color]).
 */
data class Gauge(
    val pidId: String,
    val style: GaugeStyle = GaugeStyle.DIAL,
    val color: Int = GaugeColors.palette[0],
    val warnAbove: Double? = null,
    val shiftAt: Double? = null,
    val shiftColor: Int? = null,
) {
    val flashColor: Int get() = shiftColor ?: GaugeColors.contrasting(color)

    fun isShifting(live: LiveData): Boolean {
        val at = shiftAt ?: return false
        val value = live.values[pidId] ?: return false
        return value >= at
    }

    val isMusic get() = pidId == MUSIC

    companion object {
        /** A slot showing music controls instead of a sensor. */
        const val MUSIC = "MUSIC"

        /** Only the RPM gauge offers a shift light. */
        fun supportsShiftLight(pidId: String) = pidId == "RPM"
    }
}

fun DashPage.isShifting(live: LiveData) = gauges.any { it.isShifting(live) }

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

/**
 * @param carFullScreen draw gauges across the whole car screen (Android Auto's buttons float
 *   on top and hide themselves) instead of shrinking to stay clear of them.
 * @param musicButtons which buttons music tiles show, kept in [MusicButton] order.
 * @param carMusicButtons also put the music buttons on the car screen's button bar.
 */
data class DashboardConfig(
    val pages: List<DashPage>,
    val imperial: Boolean = true,
    val carFullScreen: Boolean = true,
    val musicButtons: List<MusicButton> = DEFAULT_MUSIC_BUTTONS,
    val carMusicButtons: Boolean = false,
) {
    /** Music buttons for the car's button column; with all five chosen, "back 15s" is left out. */
    val carMusicStrip: List<MusicButton>
        get() = if (musicButtons.size > MAX_CAR_MUSIC_BUTTONS) musicButtons - MusicButton.BACK_15 else musicButtons

    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("imperial", imperial)
        put("carFullScreen", carFullScreen)
        put("musicButtons", JSONArray().apply { musicButtons.forEach { put(it.name) } })
        put("carMusicButtons", carMusicButtons)
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
                                g.shiftAt?.let { put("shiftAt", it) }
                                g.shiftColor?.let { put("shiftColor", it) }
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    companion object {
        val DEFAULT_MUSIC_BUTTONS = listOf(MusicButton.PREVIOUS, MusicButton.PLAY_PAUSE, MusicButton.NEXT)

        /** Android Auto's second button column fits four buttons. */
        const val MAX_CAR_MUSIC_BUTTONS = 4

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
                        shiftAt = if (g.has("shiftAt")) g.getDouble("shiftAt") else null,
                        shiftColor = if (g.has("shiftColor")) g.getInt("shiftColor") else null,
                    )
                }
                DashPage(p.getString("name"), p.getInt("columns"), p.getInt("rows"), gauges).let { it.resized(it.columns, it.rows) }
            }
            val buttons = root.optJSONArray("musicButtons")?.let { arr ->
                (0 until arr.length()).mapNotNull { runCatching { MusicButton.valueOf(arr.getString(it)) }.getOrNull() }.distinct().sorted()
            } ?: DEFAULT_MUSIC_BUTTONS
            return DashboardConfig(
                pages = pages.ifEmpty { default().pages },
                imperial = root.optBoolean("imperial", true),
                carFullScreen = root.optBoolean("carFullScreen", true),
                musicButtons = buttons,
                carMusicButtons = root.optBoolean("carMusicButtons", false),
            )
        }

        fun default(): DashboardConfig {
            val orange = GaugeColors.palette[0]
            val blue = GaugeColors.palette[1]
            val lime = GaugeColors.palette[2]
            return DashboardConfig(
                pages = listOf(
                    DashPage(
                        "Main", 3, 2, listOf(
                            Gauge("RPM", GaugeStyle.DIAL, orange, 6000.0, shiftAt = 5500.0),
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
                        "Cruise", 3, 1, listOf(
                            Gauge("SPEED", GaugeStyle.DIGITAL, GaugeColors.palette[6]),
                            Gauge(Gauge.MUSIC, GaugeStyle.DIGITAL, blue),
                            Gauge("FUEL_LEVEL", GaugeStyle.BAR, orange),
                        ),
                    ),
                ),
            )
        }
    }
}
