package com.beardedgoat.goatdash.obd

import com.beardedgoat.goatdash.obd.transport.ObdTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** What we learned about the adapter and car while connecting. */
data class AdapterInfo(
    val adapterLabel: String,
    val version: String,
    val protocol: String,
    val isCan: Boolean,
    val vin: String?,
    val supportedPids: Set<Int>,
)

/** Codes read from the car in one go. */
data class DtcReport(
    val milOn: Boolean,
    val stored: List<String>,
    val pending: List<String>,
    val permanent: List<String>,
    val readAtMillis: Long,
) {
    val isEmpty get() = stored.isEmpty() && pending.isEmpty() && permanent.isEmpty()
}

/**
 * Speaks the ELM327 text protocol over any [ObdTransport]. Commands are serialized with a
 * mutex because the adapter can only handle one request at a time.
 */
class Elm327(private val transport: ObdTransport) {

    private val lock = Mutex()
    var isCan = true
        private set

    /** Sends one command and returns the cleaned reply lines. */
    suspend fun send(command: String, timeoutMs: Long = 3000): List<String> = lock.withLock {
        transport.readAvailable() // Drop anything stale from a previous timed-out command.
        transport.write("$command\r".toByteArray(Charsets.US_ASCII))
        val reply = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val chunk = transport.readAvailable()
            if (chunk.isNotEmpty()) {
                reply.append(String(chunk, Charsets.US_ASCII))
                if (reply.contains('>')) break
            } else {
                if (System.currentTimeMillis() > deadline) throw IOException("Adapter stopped responding ($command)")
                delay(8)
            }
        }
        ObdParser.cleanLines(reply.toString(), command)
    }

    /** Resets the adapter, finds the car's protocol and collects basic info. */
    suspend fun initialize(): AdapterInfo {
        val version = send("ATZ", 5000).lastOrNull() ?: "ELM327"
        delay(300)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) send(cmd)

        // The first real request makes the adapter search for the car's protocol (can be slow).
        val probe = send("0100", 15_000)
        ObdParser.errorOf(probe)?.let {
            throw IOException(
                if (it == "UNABLE TO CONNECT" || it == "NO DATA") "The adapter can't talk to the car. Turn the ignition on (or start the engine) and try again."
                else "Adapter error: $it",
            )
        }
        val supported = ObdParser.supportedPids(probe, 0x00).toMutableSet()
        for (base in listOf(0x20, 0x40)) {
            if (base !in supported) break
            supported += ObdParser.supportedPids(send("01%02X".format(base)), base)
        }

        val protocolReply = send("ATDPN")
        isCan = ObdParser.isCanProtocol(protocolReply)
        val vin = runCatching { ObdParser.vin(send("0902", 5000)) }.getOrNull()

        return AdapterInfo(
            adapterLabel = transport.label,
            version = version,
            protocol = ObdParser.protocolName(protocolReply),
            isCan = isCan,
            vin = vin,
            supportedPids = supported,
        )
    }

    /** Reads one live value in metric units, or null if the car didn't answer. */
    suspend fun read(pid: Pid): Double? {
        val lines = send(pid.command)
        if (pid.command == "ATRV") return ObdParser.voltage(lines)
        val number = pid.mode01Pid ?: return null
        val data = ObdParser.mode01Data(lines, number) ?: return null
        return pid.decode(data)
    }

    suspend fun readCodes(): DtcReport {
        val mil = ObdParser.milStatus(send("0101"))
        val stored = ObdParser.dtcs(send("03", 5000), 0x03, isCan)
        val pending = ObdParser.dtcs(send("07", 5000), 0x07, isCan)
        // Permanent codes (mode 0A) only exist on 2010+ cars; older ones just answer NO DATA.
        val permanent = runCatching { ObdParser.dtcs(send("0A", 5000), 0x0A, isCan) }.getOrDefault(emptyList())
        return DtcReport(mil?.first ?: stored.isNotEmpty(), stored, pending, permanent, System.currentTimeMillis())
    }

    /** Mode 04: clears codes and turns off the check-engine light. Returns true on success. */
    suspend fun clearCodes(): Boolean = send("04", 5000).any { it.replace(" ", "").startsWith("44") }
}
