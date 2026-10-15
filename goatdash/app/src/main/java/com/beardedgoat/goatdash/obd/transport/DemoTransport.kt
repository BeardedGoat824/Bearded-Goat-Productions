package com.beardedgoat.goatdash.obd.transport

import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A pretend ELM327 plugged into a pretend car. It speaks the same text protocol as a real
 * adapter, so demo mode exercises exactly the same code path — handy for trying out the
 * dashboard (or Android Auto) from the couch.
 */
class DemoTransport(private val clock: () -> Long = System::currentTimeMillis) : ObdTransport {

    override val label = "Demo car"

    private val pending = StringBuilder()
    private val output = StringBuilder()
    private var start = 0L
    private var codesCleared = false

    override suspend fun open() {
        start = clock()
    }

    override suspend fun write(data: ByteArray) {
        for (ch in String(data, Charsets.US_ASCII)) {
            if (ch == '\r') {
                val cmd = pending.toString().trim().uppercase().replace(" ", "")
                pending.clear()
                synchronized(output) { output.append(respond(cmd)).append("\r\r>") }
            } else {
                pending.append(ch)
            }
        }
    }

    override suspend fun readAvailable(): ByteArray = synchronized(output) {
        output.toString().toByteArray(Charsets.US_ASCII).also { output.clear() }
    }

    override fun close() {}

    private fun respond(cmd: String): String {
        val t = (clock() - start) / 1000.0
        // A loose "drive cycle": accelerate, cruise, slow down, repeat every 40 seconds.
        val phase = (t % 40.0) / 40.0
        val speed = max(0.0, 105 * sin(phase * PI)) // km/h
        val gear = min(5, 1 + (speed / 25).toInt())
        val rpm = if (speed < 2) 780.0 + 20 * sin(t * 3) else 1100 + speed * 55 / gear + 300 * sin(t * 1.3)
        val throttle = (12 + 70 * max(0.0, sin(phase * 2 * PI))).coerceIn(0.0, 100.0)
        val coolant = min(92.0, 25 + t * 1.5) // warms up over the first minute
        fun b(v: Double) = v.roundToInt().coerceIn(0, 255)
        fun w(v: Double) = v.roundToInt().coerceIn(0, 65535).let { "%02X%02X".format(it / 256, it % 256) }

        return when {
            cmd == "ATZ" || cmd == "ATI" -> "ELM327 v1.5 (demo)"
            cmd == "ATRV" -> String.format(Locale.US, "%.1fV", 13.9 + 0.2 * sin(t / 3))
            cmd == "ATDPN" -> "A6"
            cmd.startsWith("AT") -> "OK"
            cmd == "0100" -> "4100BE3FB813"
            cmd == "0120" -> "4120A002B011"
            cmd == "0140" -> "4140FED00014"
            cmd == "0101" -> if (codesCleared) "410100076500" else "410182076500"
            cmd == "0104" -> "4104%02X".format(b(throttle * 0.8 * 2.55 + 40))
            cmd == "0105" -> "4105%02X".format(b(coolant + 40))
            cmd == "0106" -> "4106%02X".format(b((100 + 3 * sin(t)) * 1.28))
            cmd == "0107" -> "4107%02X".format(b(104 * 1.28))
            cmd == "010B" -> "410B%02X".format(b(30 + throttle * 0.7))
            cmd == "010C" -> "410C" + w(rpm * 4)
            cmd == "010D" -> "410D%02X".format(b(speed))
            cmd == "010E" -> "410E%02X".format(b((15 + 10 * sin(t / 2) + 64) * 2))
            cmd == "010F" -> "410F%02X".format(b(min(45.0, 22 + t / 10) + 40))
            cmd == "0110" -> "4110" + w((3 + rpm / 400 + throttle * 0.5) * 100)
            cmd == "0111" -> "4111%02X".format(b(throttle * 2.55))
            cmd == "012F" -> "412F%02X".format(b(max(5.0, 68 - t / 60) * 2.55))
            cmd == "0133" -> "413365"
            cmd == "0142" -> "4142" + w((13.9 + 0.2 * sin(t / 3)) * 1000)
            cmd == "0146" -> "414640" // 24 °C
            cmd == "015C" -> "415C%02X".format(b(min(105.0, 25 + t * 1.2) + 40))
            cmd == "015E" -> "415E" + w((0.8 + throttle * 0.15) * 20)
            cmd == "03" -> if (codesCleared) "4300" else "430203010420" // P0301, P0420
            cmd == "07" -> if (codesCleared) "4700" else "47010171" // P0171 pending
            cmd == "0A" -> "4A00"
            cmd == "04" -> { codesCleared = true; "44" }
            cmd == "0902" -> "014\r0:490201314731\r1:4A433534343452\r2:37323532333637"
            else -> "NO DATA"
        }
    }
}
