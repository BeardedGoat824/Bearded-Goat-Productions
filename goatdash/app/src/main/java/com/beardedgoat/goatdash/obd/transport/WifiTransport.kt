package com.beardedgoat.goatdash.obd.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Wi-Fi adapters create their own hotspot; join it in Android's Wi-Fi settings first.
 * Most use 192.168.0.10 port 35000.
 */
class WifiTransport(private val host: String, private val port: Int) : ObdTransport {

    override val label: String = "$host:$port (Wi-Fi)"

    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 5000)
                soTimeout = 20
                tcpNoDelay = true
            }
        } catch (e: IOException) {
            throw IOException("Couldn't reach $host:$port. Are you connected to the adapter's Wi-Fi network?", e)
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Not connected")
        s.getOutputStream().write(data)
        s.getOutputStream().flush()
    }

    override suspend fun readAvailable(): ByteArray = withContext(Dispatchers.IO) {
        val input = socket?.getInputStream() ?: throw IOException("Not connected")
        val buf = ByteArray(1024)
        try {
            val n = input.read(buf)
            if (n < 0) throw IOException("Adapter closed the connection")
            buf.copyOf(n)
        } catch (_: SocketTimeoutException) {
            ByteArray(0)
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}
