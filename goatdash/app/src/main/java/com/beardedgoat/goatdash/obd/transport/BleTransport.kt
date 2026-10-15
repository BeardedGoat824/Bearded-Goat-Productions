package com.beardedgoat.goatdash.obd.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Low Energy adapters (Vgate iCar Pro BLE, OBDLink CX, Veepeak BLE, ...).
 * They don't pair in Android settings; they show up in the in-app scan instead.
 */
@SuppressLint("MissingPermission") // Permissions are requested on the Connect screen before we get here.
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : ObdTransport {

    override val label: String = "${device.name ?: device.address} (Bluetooth LE)"

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Unit>()
    private var pendingWrite: CompletableDeferred<Unit>? = null
    private val rx = ByteArrayOutputStream()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED ->
                    if (!ready.isCompleted) ready.completeExceptionally(IOException("Adapter disconnected (status $status)"))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val (notify, write) = pickCharacteristics(g) ?: run {
                ready.completeExceptionally(IOException("This Bluetooth LE device doesn't look like an OBD2 adapter"))
                return
            }
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD)
            if (cccd == null) {
                ready.complete(Unit)
                return
            }
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            ready.complete(Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(Unit)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            synchronized(rx) { rx.write(value) }
        }

        // Android 12 and older
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                c.value?.let { synchronized(rx) { rx.write(it) } }
            }
        }
    }

    override suspend fun open() {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { ready.await() }
        } catch (e: Exception) {
            close()
            throw if (e is IOException) e else IOException("Timed out connecting to ${device.name ?: device.address}", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        val g = gatt ?: throw IOException("Not connected")
        val c = writeChar ?: throw IOException("Not connected")
        val withResponse = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        // Default BLE packets carry 20 bytes; OBD commands are short but split just in case.
        for (chunk in data.toList().chunked(20)) {
            val bytes = chunk.toByteArray()
            val done = CompletableDeferred<Unit>().also { pendingWrite = it }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, type)
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            withTimeoutOrNull(1000) { done.await() }
        }
    }

    override suspend fun readAvailable(): ByteArray = synchronized(rx) {
        rx.toByteArray().also { rx.reset() }
    }

    override fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Serial-over-BLE services used by common OBD adapters, best guesses first. */
        private val knownServices = listOf("fff0", "ffe0", "18f0", "e7810a71")

        private fun BluetoothGattCharacteristic.can(flag: Int) = properties and flag != 0

        private fun pickCharacteristics(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
            val services = g.services.sortedBy { s ->
                val id = s.uuid.toString().lowercase()
                knownServices.indexOfFirst { id.startsWith("0000$it") || id.startsWith(it) }.let { if (it < 0) 99 else it }
            }
            for (service in services) {
                val id = service.uuid.toString().lowercase()
                // Skip the standard GAP/GATT/device-info services.
                if (id.startsWith("00001800") || id.startsWith("00001801") || id.startsWith("0000180a")) continue
                val notify = service.characteristics.firstOrNull {
                    it.can(BluetoothGattCharacteristic.PROPERTY_NOTIFY) || it.can(BluetoothGattCharacteristic.PROPERTY_INDICATE)
                }
                val write = service.characteristics.firstOrNull {
                    it.can(BluetoothGattCharacteristic.PROPERTY_WRITE) || it.can(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                }
                if (notify != null && write != null) return notify to write
            }
            return null
        }
    }
}
