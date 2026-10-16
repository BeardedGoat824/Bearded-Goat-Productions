package com.beardedgoat.goatdash.obd.transport

/** A byte pipe to an ELM327-compatible adapter (Bluetooth, BLE, Wi-Fi or the built-in demo). */
interface ObdTransport {
    /** Human-readable name shown in the UI, e.g. "OBDII (Bluetooth)". */
    val label: String

    /** Connects. Throws with a readable message if the adapter can't be reached. */
    suspend fun open()

    suspend fun write(data: ByteArray)

    /** Returns whatever bytes have arrived so far — possibly none. Never blocks for long. */
    suspend fun readAvailable(): ByteArray

    fun close()
}
