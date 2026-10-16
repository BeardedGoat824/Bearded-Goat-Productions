package com.beardedgoat.goatdash

import com.beardedgoat.goatdash.dash.DashboardConfig
import com.beardedgoat.goatdash.dash.GaugeStyle
import com.beardedgoat.goatdash.obd.ConnectionTarget
import com.beardedgoat.goatdash.obd.Elm327
import com.beardedgoat.goatdash.obd.Pids
import com.beardedgoat.goatdash.obd.transport.DemoTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the real ELM327 driver against the built-in demo car, end to end. */
class Elm327DemoTest {

    @Test
    fun connectsReadsAndClears() = runBlocking {
        var now = 0L
        val transport = DemoTransport { now }.also { it.open() }
        val elm = Elm327(transport)

        val info = elm.initialize()
        assertTrue(info.isCan)
        assertEquals("1G1JC5444R7252367", info.vin)
        assertTrue(0x0C in info.supportedPids)
        assertTrue(0x2F in info.supportedPids) // from the second (0120) block
        assertTrue(0x5C in info.supportedPids) // from the third (0140) block

        now = 60_000 // a minute in: engine warm
        val coolant = elm.read(Pids["COOLANT"]!!)
        assertEquals(92.0, coolant!!, 1.0)
        assertNotNull(elm.read(Pids["RPM"]!!))
        assertEquals(13.9, elm.read(Pids["BATTERY"]!!)!!, 0.3)

        val report = elm.readCodes()
        assertTrue(report.milOn)
        assertEquals(listOf("P0301", "P0420"), report.stored)
        assertEquals(listOf("P0171"), report.pending)

        assertTrue(elm.clearCodes())
        val after = elm.readCodes()
        assertFalse(after.milOn)
        assertTrue(after.isEmpty)
    }

    @Test
    fun dashboardConfigRoundTrips() {
        val original = DashboardConfig.default().copy(imperial = false)
        val restored = DashboardConfig.fromJson(original.toJson())
        assertEquals(original, restored)
        assertEquals(GaugeStyle.DIAL, restored.pages[0].gauges[0].style)
    }

    @Test
    fun resizingKeepsGaugesAndFillsNewSlots() {
        val page = DashboardConfig.default().pages[0] // 3x2
        val bigger = page.resized(4, 3)
        assertEquals(12, bigger.gauges.size)
        assertEquals(page.gauges, bigger.gauges.take(6))
        val smaller = page.resized(1, 1)
        assertEquals(listOf(page.gauges[0]), smaller.gauges)
        assertEquals(4, page.resized(9, 9).columns) // clamped
    }

    @Test
    fun connectionTargetsSurviveSaving() {
        listOf(
            ConnectionTarget.Classic("00:11:22:33:44:55", "OBDII"),
            ConnectionTarget.Ble("AA:BB:CC:DD:EE:FF", "IOS-Vlink|weird name"),
            ConnectionTarget.Wifi("192.168.0.10", 35000),
            ConnectionTarget.Demo,
        ).forEach { assertEquals(it, ConnectionTarget.decode(it.encode())) }
    }
}
