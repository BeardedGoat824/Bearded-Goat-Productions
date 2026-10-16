package com.beardedgoat.goatdash.obd.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Classic Bluetooth (SPP) adapters — the most common cheap ELM327 dongles. */
@SuppressLint("MissingPermission") // Permissions are requested on the Connect screen before we get here.
class BluetoothClassicTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) : ObdTransport {

    override val label: String = "${device.name ?: device.address} (Bluetooth)"

    private var socket: BluetoothSocket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        adapter.cancelDiscovery() // Discovery slows down or breaks RFCOMM connects.
        socket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
        } catch (first: IOException) {
            // Many clone adapters only accept insecure connections.
            try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
            } catch (second: IOException) {
                throw IOException("Couldn't connect to ${device.name ?: device.address}. Is the car on and the adapter plugged in?", second)
            }
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Not connected")
        s.outputStream.write(data)
        s.outputStream.flush()
    }

    override suspend fun readAvailable(): ByteArray = withContext(Dispatchers.IO) {
        val input = socket?.inputStream ?: throw IOException("Not connected")
        val n = input.available()
        if (n <= 0) ByteArray(0) else ByteArray(n).let { buf -> buf.copyOf(input.read(buf, 0, n)) }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        /** Standard Serial Port Profile UUID used by ELM327 adapters. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
