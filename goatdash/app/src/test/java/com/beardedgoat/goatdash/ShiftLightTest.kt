package com.beardedgoat.goatdash

import com.beardedgoat.goatdash.dash.DashPage
import com.beardedgoat.goatdash.dash.DashboardConfig
import com.beardedgoat.goatdash.dash.Gauge
import com.beardedgoat.goatdash.dash.GaugeColors
import com.beardedgoat.goatdash.dash.isShifting
import com.beardedgoat.goatdash.obd.LiveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLightTest {

    private fun rgb(c: Int) = Triple(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    @Test
    fun contrastingColourIsOppositeOnTheWheel() {
        assertEquals(0xFF00FFFF.toInt(), GaugeColors.contrasting(0xFFFF0000.toInt())) // red -> cyan
        assertEquals(0xFFFF00FF.toInt(), GaugeColors.contrasting(0xFF00FF00.toInt())) // green -> magenta
        assertEquals(0xFFFFFF00.toInt(), GaugeColors.contrasting(0xFF0000FF.toInt())) // blue -> yellow
        // Goat orange flashes blue.
        val (r, _, b) = rgb(GaugeColors.contrasting(GaugeColors.palette[0]))
        assertEquals(0, r)
        assertEquals(255, b)
    }

    @Test
    fun greysAndWhiteGetCyan() {
        assertEquals(0xFF00E5FF.toInt(), GaugeColors.contrasting(0xFFF1F5F9.toInt()))
        assertEquals(0xFF00E5FF.toInt(), GaugeColors.contrasting(0xFF000000.toInt()))
    }

    @Test
    fun flashColourPrefersTheChosenOne() {
        assertEquals(0xFFFFEA00.toInt(), Gauge("RPM", color = 0xFFFF0000.toInt(), shiftColor = 0xFFFFEA00.toInt()).flashColor)
        assertEquals(0xFF00FFFF.toInt(), Gauge("RPM", color = 0xFFFF0000.toInt()).flashColor)
    }

    @Test
    fun shiftsAtAndAboveTheSetPoint() {
        val gauge = Gauge("RPM", shiftAt = 5500.0)
        assertFalse(gauge.isShifting(LiveData(values = mapOf("RPM" to 5499.0))))
        assertTrue(gauge.isShifting(LiveData(values = mapOf("RPM" to 5500.0))))
        assertTrue(gauge.isShifting(LiveData(values = mapOf("RPM" to 7000.0))))
        assertFalse(gauge.isShifting(LiveData())) // no reading yet
        assertFalse(Gauge("RPM").isShifting(LiveData(values = mapOf("RPM" to 9000.0)))) // shift light off

        val page = DashPage("P", 2, 1, listOf(Gauge("SPEED"), gauge))
        assertTrue(page.isShifting(LiveData(values = mapOf("RPM" to 6000.0))))
        assertFalse(page.isShifting(LiveData(values = mapOf("RPM" to 3000.0))))
    }

    @Test
    fun shiftSettingsAreSaved() {
        val cfg = DashboardConfig(
            pages = listOf(DashPage("P", 1, 1, listOf(Gauge("RPM", shiftAt = 6200.0, shiftColor = 0xFF2979FF.toInt())))),
        )
        assertEquals(cfg, DashboardConfig.fromJson(cfg.toJson()))
        // Layouts saved before shift lights existed still load, with the light off.
        val old = """{"pages":[{"name":"P","columns":1,"rows":1,"gauges":[{"pid":"RPM","style":"DIAL","color":-30177}]}]}"""
        val g = DashboardConfig.fromJson(old).pages[0].gauges[0]
        assertEquals(null, g.shiftAt)
        assertEquals(null, g.shiftColor)
    }

    @Test
    fun onlyRpmOffersAShiftLight() {
        assertTrue(Gauge.supportsShiftLight("RPM"))
        assertFalse(Gauge.supportsShiftLight("SPEED"))
    }
}
