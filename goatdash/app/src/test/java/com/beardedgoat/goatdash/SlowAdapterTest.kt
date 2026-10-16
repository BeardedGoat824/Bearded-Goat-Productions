package com.beardedgoat.goatdash

import com.beardedgoat.goatdash.obd.AdapterTimeoutException
import com.beardedgoat.goatdash.obd.Elm327
import com.beardedgoat.goatdash.obd.ObdParser
import com.beardedgoat.goatdash.obd.transport.ObdTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Clone adapters sometimes answer late. That late answer must not be read as the next reply. */
class SlowAdapterTest {

    /** Answers every command, but the first one only after [firstDelayMs]. */
    private class SlowTransport(private val firstDelayMs: Long) : ObdTransport {
        override val label = "slow"
        private val pending = mutableListOf<Pair<Long, String>>()
        private var count = 0

        override suspend fun open() {}
        override fun close() {}

        override suspend fun write(data: ByteArray) {
            val cmd = String(data).trim()
            val delay = if (count++ == 0) firstDelayMs else 0
            val reply = when (cmd) {
                "010C" -> "410C1AF8"
                "010D" -> "410D32"
                else -> "OK"
            }
            synchronized(pending) { pending += (System.currentTimeMillis() + delay) to "$reply\r\r>" }
        }

        override suspend fun readAvailable(): ByteArray = synchronized(pending) {
            val now = System.currentTimeMillis()
            val ready = pending.filter { it.first <= now }
            pending.removeAll(ready)
            ready.joinToString("") { it.second }.toByteArray()
        }
    }

    @Test
    fun lateAnswerIsNotMistakenForTheNextReply() = runBlocking {
        val elm = Elm327(SlowTransport(firstDelayMs = 400))
        try {
            elm.send("010C", timeoutMs = 100)
            fail("expected a timeout")
        } catch (_: AdapterTimeoutException) {
        }
        // The RPM answer turns up late; the speed request must still get the speed answer.
        val reply = elm.send("010D", timeoutMs = 1000)
        assertEquals(0x32, ObdParser.mode01Data(reply, 0x0D)!![0])
    }
}
