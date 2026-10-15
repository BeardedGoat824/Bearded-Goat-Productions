package com.beardedgoat.goatdash

import com.beardedgoat.goatdash.obd.DtcDescriptions
import com.beardedgoat.goatdash.obd.ObdParser
import com.beardedgoat.goatdash.obd.Pids
import com.beardedgoat.goatdash.obd.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    private fun lines(raw: String, cmd: String? = null) = ObdParser.cleanLines(raw, cmd)

    @Test
    fun cleansPromptEchoAndSearching() {
        assertEquals(listOf("41 0C 1A F8"), lines("010C\rSEARCHING...\r41 0C 1A F8\r\r>", "010C"))
    }

    @Test
    fun decodesRpmWithAndWithoutSpaces() {
        val pid = Pids["RPM"]!!
        assertEquals(1726.0, pid.decode(ObdParser.mode01Data(lines("41 0C 1A F8\r>"), 0x0C)!!)!!, 0.01)
        assertEquals(1726.0, pid.decode(ObdParser.mode01Data(lines("410C1AF8\r>"), 0x0C)!!)!!, 0.01)
    }

    @Test
    fun decodesCommonFormulas() {
        assertEquals(90.0, Pids["COOLANT"]!!.decode(intArrayOf(130))!!, 0.0)
        assertEquals(100.0, Pids["THROTTLE"]!!.decode(intArrayOf(255))!!, 0.01)
        assertEquals(-100.0, Pids["STFT1"]!!.decode(intArrayOf(0))!!, 0.01)
        assertEquals(13.9, Pids["ECU_VOLT"]!!.decode(intArrayOf(0x36, 0x4C))!!, 0.001)
        assertNull(Pids["RPM"]!!.decode(intArrayOf(1)))
    }

    @Test
    fun picksTheRightEcuLine() {
        // Two ECUs answer; we want the first one that contains the requested PID.
        val data = ObdParser.mode01Data(lines("7E8 03 41 0D 32\r410D32\r>"), 0x0D)
        assertEquals(0x32, data!![0])
    }

    @Test
    fun voltageFromAtrv() {
        assertEquals(12.6, ObdParser.voltage(lines("12.6V\r>"))!!, 0.0)
    }

    @Test
    fun recognisesErrors() {
        assertEquals("NO DATA", ObdParser.errorOf(lines("NO DATA\r>")))
        assertEquals("UNABLE TO CONNECT", ObdParser.errorOf(lines("SEARCHING...\rUNABLE TO CONNECT\r>")))
        assertNull(ObdParser.errorOf(lines("410C1AF8\r>")))
    }

    @Test
    fun decodesDtcBytes() {
        assertEquals("P0301", ObdParser.decodeDtc(0x03, 0x01))
        assertEquals("P0420", ObdParser.decodeDtc(0x04, 0x20))
        assertEquals("C0035", ObdParser.decodeDtc(0x40, 0x35))
        assertEquals("B1234", ObdParser.decodeDtc(0x92, 0x34))
        assertEquals("U0100", ObdParser.decodeDtc(0xC1, 0x00))
        assertNull(ObdParser.decodeDtc(0, 0))
    }

    @Test
    fun parsesCanDtcsWithCount() {
        assertEquals(listOf("P0301", "P0420"), ObdParser.dtcs(lines("43 02 03 01 04 20\r>"), 0x03, isCan = true))
        assertEquals(emptyList<String>(), ObdParser.dtcs(lines("4300\r>"), 0x03, isCan = true))
    }

    @Test
    fun parsesMultiFrameCanDtcs() {
        val raw = "00A\r0:43040133014401\r1:45014600000000\r>"
        assertEquals(listOf("P0133", "P0144", "P0145", "P0146"), ObdParser.dtcs(lines(raw), 0x03, isCan = true))
    }

    @Test
    fun parsesLegacyDtcsAcrossLines() {
        val raw = "43 01 33 01 44 00 00\r43 01 71 00 00 00 00\r>"
        assertEquals(listOf("P0133", "P0144", "P0171"), ObdParser.dtcs(lines(raw), 0x03, isCan = false))
    }

    @Test
    fun milStatus() {
        assertEquals(true to 2, ObdParser.milStatus(lines("41 01 82 07 65 00\r>")))
        assertEquals(false to 0, ObdParser.milStatus(lines("41 01 00 07 65 00\r>")))
    }

    @Test
    fun supportedPids() {
        val s = ObdParser.supportedPids(lines("41 00 BE 3F B8 13\r>"), 0x00)
        assertTrue(0x0C in s && 0x0D in s && 0x05 in s && 0x20 in s)
        assertFalse(0x02 in s)
    }

    @Test
    fun parsesMultiFrameVin() {
        val raw = "014\r0:490201314731\r1:4A433534343452\r2:37323532333637\r>"
        assertEquals("1G1JC5444R7252367", ObdParser.vin(lines(raw)))
    }

    @Test
    fun protocolDetection() {
        assertTrue(ObdParser.isCanProtocol(listOf("A6")))
        assertTrue(ObdParser.isCanProtocol(listOf("8")))
        assertFalse(ObdParser.isCanProtocol(listOf("A3")))
        assertEquals("ISO 15765-4 CAN (11 bit, 500 kbaud)", ObdParser.protocolName(listOf("A6")))
    }

    @Test
    fun unitConversionsRoundTrip() {
        assertEquals(62.1371, UnitKind.SPEED.toDisplay(100.0, imperial = true), 0.001)
        assertEquals(212.0, UnitKind.TEMP.toDisplay(100.0, imperial = true), 0.001)
        assertEquals(90.0, UnitKind.TEMP.fromDisplay(UnitKind.TEMP.toDisplay(90.0, true), true), 0.0001)
        assertEquals(100.0, UnitKind.SPEED.toDisplay(100.0, imperial = false), 0.0)
    }

    @Test
    fun describesCodes() {
        assertEquals("Cylinder 1 misfire detected", DtcDescriptions.describe("P0301"))
        assertTrue(DtcDescriptions.describe("P1234").startsWith("Manufacturer-specific"))
        assertEquals("Ignition system or misfire", DtcDescriptions.describe("P0399"))
        assertTrue(DtcDescriptions.describe("C0035").contains("chassis", ignoreCase = true))
    }
}
