package com.beardedgoat.goatdash.obd

/**
 * Turns raw ELM327 text into usable data. Everything here is pure Kotlin so it can be unit
 * tested without a phone or a car.
 */
object ObdParser {

    /** Replies the ELM327 sends instead of data. */
    private val errorReplies = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY", "BUFFER FULL",
        "DATA ERROR", "FB ERROR", "LV RESET", "STOPPED", "ERROR", "?",
    )

    /** Splits a reply into trimmed lines, dropping the prompt, echoes and status chatter. */
    fun cleanLines(raw: String, sentCommand: String? = null): List<String> =
        raw.replace(">", "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("SEARCHING") &&
                    !line.startsWith("BUS INIT") &&
                    (sentCommand == null || !line.replace(" ", "").equals(sentCommand.replace(" ", ""), ignoreCase = true))
            }

    /** The error text if the adapter replied with one of its error messages, else null. */
    fun errorOf(lines: List<String>): String? =
        lines.firstNotNullOfOrNull { line -> errorReplies.firstOrNull { line.uppercase().startsWith(it) } }

    private val isoTpFrame = Regex("^[0-9A-F]:")

    /**
     * Joins the lines of a reply into separate messages (one per ECU that answered), as hex
     * strings without spaces. Multi-frame CAN replies ("0:4902..." "1:...") are stitched back
     * together and their leading length line is skipped.
     */
    fun messages(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var multi: StringBuilder? = null
        for (raw in lines) {
            val line = raw.replace(" ", "").uppercase()
            if (isoTpFrame.containsMatchIn(line)) {
                if (line.startsWith("0:")) {
                    multi?.let { out += it.toString() }
                    multi = StringBuilder()
                }
                (multi ?: StringBuilder().also { multi = it }).append(line.substring(2))
            } else {
                multi?.let { out += it.toString() }
                multi = null
                // A bare 3-digit length header that precedes multi-frame data.
                if (line.length == 3 && line.all { it.isHexDigit() }) continue
                if (line.isNotEmpty() && line.all { it.isHexDigit() }) out += line
            }
        }
        multi?.let { out += it.toString() }
        return out
    }

    fun hexToBytes(hex: String): IntArray =
        IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }

    /** Data bytes of a mode 01 reply for [pid], e.g. "410C1AF8" -> [0x1A, 0xF8]. */
    fun mode01Data(lines: List<String>, pid: Int): IntArray? {
        val marker = "41%02X".format(pid)
        for (msg in messages(lines)) {
            val i = msg.indexOf(marker)
            if (i >= 0 && i % 2 == 0) return hexToBytes(msg.substring(i + marker.length).let { it.substring(0, it.length - it.length % 2) })
        }
        return null
    }

    /** Volts from an "ATRV" reply like "12.6V". */
    fun voltage(lines: List<String>): Double? =
        lines.firstNotNullOfOrNull { Regex("""(\d+(\.\d+)?)\s*V""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

    /**
     * Supported-PID bitmap from a 0100/0120/0140... reply. Returns the PID numbers that the
     * car says it supports in the 32-PID block starting after [base].
     */
    fun supportedPids(lines: List<String>, base: Int): Set<Int> {
        val data = mode01Data(lines, base) ?: return emptySet()
        val result = mutableSetOf<Int>()
        for (byteIndex in 0 until minOf(4, data.size)) {
            for (bit in 0 until 8) {
                if (data[byteIndex] and (0x80 shr bit) != 0) result += base + byteIndex * 8 + bit + 1
            }
        }
        return result
    }

    /** Turns two raw bytes into a code like "P0301". Returns null for the empty "0000" slot. */
    fun decodeDtc(a: Int, b: Int): String? {
        if (a == 0 && b == 0) return null
        val letter = "PCBU"[(a shr 6) and 0x03]
        val first = (a shr 4) and 0x03
        return "%c%d%X%02X".format(letter, first, a and 0x0F, b)
    }

    /**
     * Parses a reply to mode 03 (stored), 07 (pending) or 0A (permanent).
     *
     * On CAN cars ([isCan]) each message is `<43> <count> <code pairs...>`; on older
     * protocols each line is `<43> <three code pairs>` padded with zeros.
     */
    fun dtcs(lines: List<String>, mode: Int, isCan: Boolean): List<String> {
        val responseByte = "%02X".format(mode + 0x40)
        val codes = linkedSetOf<String>()
        for (msg in messages(lines)) {
            if (!msg.startsWith(responseByte)) continue
            val bytes = hexToBytes(msg.substring(2))
            val pairs = if (isCan && bytes.isNotEmpty()) {
                val count = bytes[0]
                bytes.drop(1).take(count * 2)
            } else {
                bytes.toList()
            }
            for (i in 0 until pairs.size - 1 step 2) decodeDtc(pairs[i], pairs[i + 1])?.let { codes += it }
        }
        return codes.toList()
    }

    /** Check-engine light state and stored-code count from a "0101" reply. */
    fun milStatus(lines: List<String>): Pair<Boolean, Int>? {
        val data = mode01Data(lines, 0x01) ?: return null
        if (data.isEmpty()) return null
        return (data[0] and 0x80 != 0) to (data[0] and 0x7F)
    }

    /** The 17-character VIN from a "0902" reply, if the car provides it. */
    fun vin(lines: List<String>): String? {
        val msg = messages(lines).firstOrNull { it.contains("4902") } ?: return null
        val chars = hexToBytes(msg.substring(msg.indexOf("4902") + 4).let { it.substring(0, it.length - it.length % 2) })
            .map { it.toChar() }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
        return if (chars.length >= 17) chars.takeLast(17) else null
    }

    /** True for OBD protocols 6–9 (CAN), from an "ATDPN" reply like "A6" or "6". */
    fun isCanProtocol(lines: List<String>): Boolean {
        val p = lines.firstOrNull()?.trim()?.removePrefix("A") ?: return true
        return p in setOf("6", "7", "8", "9")
    }

    private val protocolNames = mapOf(
        "1" to "SAE J1850 PWM", "2" to "SAE J1850 VPW", "3" to "ISO 9141-2",
        "4" to "ISO 14230-4 KWP (5 baud)", "5" to "ISO 14230-4 KWP (fast)",
        "6" to "ISO 15765-4 CAN (11 bit, 500 kbaud)", "7" to "ISO 15765-4 CAN (29 bit, 500 kbaud)",
        "8" to "ISO 15765-4 CAN (11 bit, 250 kbaud)", "9" to "ISO 15765-4 CAN (29 bit, 250 kbaud)",
        "A" to "SAE J1939 CAN",
    )

    fun protocolName(lines: List<String>): String {
        val p = lines.firstOrNull()?.trim()?.let { if (it.length == 2 && it.startsWith("A")) it.substring(1) else it } ?: return "Unknown"
        return protocolNames[p] ?: "Protocol $p"
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'A'..'F'
}
