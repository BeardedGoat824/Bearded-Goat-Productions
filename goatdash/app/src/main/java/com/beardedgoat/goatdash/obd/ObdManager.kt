package com.beardedgoat.goatdash.obd

import android.bluetooth.BluetoothManager
import android.content.Context
import com.beardedgoat.goatdash.dash.DashboardStore
import com.beardedgoat.goatdash.obd.transport.BleTransport
import com.beardedgoat.goatdash.obd.transport.BluetoothClassicTransport
import com.beardedgoat.goatdash.obd.transport.DemoTransport
import com.beardedgoat.goatdash.obd.transport.ObdTransport
import com.beardedgoat.goatdash.obd.transport.WifiTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/** Where to find the adapter. Saved so we can reconnect automatically next time. */
sealed class ConnectionTarget {
    data class Classic(val address: String, val name: String) : ConnectionTarget()
    data class Ble(val address: String, val name: String) : ConnectionTarget()
    data class Wifi(val host: String, val port: Int) : ConnectionTarget()
    data object Demo : ConnectionTarget()

    val displayName: String
        get() = when (this) {
            is Classic -> "$name (Bluetooth)"
            is Ble -> "$name (Bluetooth LE)"
            is Wifi -> "$host:$port (Wi-Fi)"
            Demo -> "Demo car"
        }

    fun encode(): String = when (this) {
        is Classic -> "classic|$address|$name"
        is Ble -> "ble|$address|$name"
        is Wifi -> "wifi|$host|$port"
        Demo -> "demo"
    }

    companion object {
        fun decode(s: String): ConnectionTarget? {
            val parts = s.split("|")
            return when (parts[0]) {
                "classic" -> if (parts.size >= 3) Classic(parts[1], parts.drop(2).joinToString("|")) else null
                "ble" -> if (parts.size >= 3) Ble(parts[1], parts.drop(2).joinToString("|")) else null
                "wifi" -> if (parts.size == 3) parts[2].toIntOrNull()?.let { Wifi(parts[1], it) } else null
                "demo" -> Demo
                else -> null
            }
        }
    }
}

sealed class ConnState {
    data object Disconnected : ConnState()
    data class Connecting(val target: ConnectionTarget, val step: String) : ConnState()
    data class Connected(val target: ConnectionTarget, val info: AdapterInfo) : ConnState()
    data class Failed(val target: ConnectionTarget, val message: String) : ConnState()
}

/** Latest readings (metric) plus a short history per sensor for graph gauges. */
data class LiveData(
    val values: Map<String, Double> = emptyMap(),
    val history: Map<String, List<Float>> = emptyMap(),
    val unsupported: Set<String> = emptySet(),
)

data class CodesState(
    val report: DtcReport? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class ObdManager(
    private val context: Context,
    private val dashboard: DashboardStore,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("obd", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _live = MutableStateFlow(LiveData())
    val live: StateFlow<LiveData> = _live.asStateFlow()

    private val _codes = MutableStateFlow(CodesState())
    val codes: StateFlow<CodesState> = _codes.asStateFlow()

    private var job: Job? = null
    private var transport: ObdTransport? = null
    private var elm: Elm327? = null

    val lastTarget: ConnectionTarget?
        get() = prefs.getString("last_target", null)?.let { ConnectionTarget.decode(it) }

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", true)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    /** Reconnects to the last adapter if we're idle — used on app launch and when the car connects. */
    fun autoConnectIfIdle() {
        val target = lastTarget ?: return
        if (!autoConnect) return
        if (_state.value is ConnState.Disconnected || _state.value is ConnState.Failed) connect(target)
    }

    fun connect(target: ConnectionTarget) {
        disconnect()
        job = scope.launch {
            var everConnected = false
            var retries = 0
            while (isActive) {
                val t = try {
                    buildTransport(target)
                } catch (e: Exception) {
                    _state.value = ConnState.Failed(target, e.message ?: "Couldn't use this adapter")
                    return@launch
                }
                transport = t
                try {
                    if (!everConnected) _state.value = ConnState.Connecting(target, "Connecting to adapter…")
                    t.open()
                    if (!everConnected) _state.value = ConnState.Connecting(target, "Talking to the car…")
                    val e = Elm327(t)
                    val info = e.initialize()
                    elm = e
                    prefs.edit().putString("last_target", target.encode()).apply()
                    // Keep what we learned about unsupported sensors across reconnects.
                    _live.update { it.copy(unsupported = it.unsupported + unsupportedFrom(info)) }
                    _state.value = ConnState.Connected(target, info)
                    everConnected = true
                    retries = 0
                    ObdService.start(context)
                    pollLoop(e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A first connect that fails is reported straight away. Once we've been
                    // connected, a drop is usually a Bluetooth hiccup: quietly try again.
                    if (!everConnected || ++retries > MAX_RECONNECTS) {
                        _state.value = ConnState.Failed(
                            target,
                            if (everConnected) "Lost the connection and couldn't get it back. Is the car still on?" else e.message ?: e.javaClass.simpleName,
                        )
                        return@launch
                    }
                    _state.value = ConnState.Connecting(target, "Connection dropped, reconnecting (try $retries of $MAX_RECONNECTS)…")
                } finally {
                    t.close()
                    if (transport === t) {
                        transport = null
                        elm = null
                    }
                }
                delay(minOf(2_000L * retries, 10_000L))
            }
        }
        job?.invokeOnCompletion {
            // Only tidy up if a newer connect()/disconnect() hasn't already taken over.
            if (transport == null && _state.value !is ConnState.Connected) {
                _live.update { LiveData(unsupported = it.unsupported) }
                ObdService.stop(context)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        transport?.close()
        transport = null
        elm = null
        _state.value = ConnState.Disconnected
        _live.value = LiveData()
        ObdService.stop(context)
    }

    private fun buildTransport(target: ConnectionTarget): ObdTransport = when (target) {
        is ConnectionTarget.Classic, is ConnectionTarget.Ble -> {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IOException("This phone has no Bluetooth")
            if (!adapter.isEnabled) throw IOException("Bluetooth is turned off")
            if (target is ConnectionTarget.Classic) BluetoothClassicTransport(adapter, adapter.getRemoteDevice(target.address))
            else BleTransport(context, adapter.getRemoteDevice((target as ConnectionTarget.Ble).address))
        }
        is ConnectionTarget.Wifi -> WifiTransport(target.host, target.port)
        ConnectionTarget.Demo -> DemoTransport()
    }

    private fun unsupportedFrom(info: AdapterInfo): Set<String> =
        if (info.supportedPids.isEmpty()) emptySet()
        else Pids.all.filter { p -> p.mode01Pid != null && p.mode01Pid !in info.supportedPids }.map { it.id }.toSet()

    /** Reads the sensors on the page that's showing, round-robin, as fast as the adapter allows. */
    private suspend fun pollLoop(e: Elm327) {
        val misses = mutableMapOf<String, Int>()
        var errorsInARow = 0
        while (currentCoroutineContext().isActive) {
            val cfg = dashboard.config.value
            val page = cfg.pages.getOrNull(dashboard.currentPage.value) ?: cfg.pages.first()
            val unsupported = _live.value.unsupported
            val pids = page.gauges.map { it.pidId }.distinct().filter { it !in unsupported }.mapNotNull { Pids[it] }
            if (pids.isEmpty()) {
                delay(250)
                continue
            }
            for (pid in pids) {
                val value = try {
                    e.read(pid).also { errorsInARow = 0 }
                } catch (slow: AdapterTimeoutException) {
                    // Clones sometimes stall for a few seconds; only give up if it keeps happening.
                    if (++errorsInARow >= MAX_TIMEOUTS_IN_A_ROW) throw IOException("Adapter stopped answering")
                    continue
                }
                // A real Bluetooth/Wi-Fi break throws a plain IOException and goes straight to reconnecting.
                if (value == null) {
                    // Some cars just don't report some sensors; stop asking after a few misses.
                    if (misses.merge(pid.id, 1, Int::plus)!! >= 4) _live.update { it.copy(unsupported = it.unsupported + pid.id) }
                    continue
                }
                misses.remove(pid.id)
                _live.update { live ->
                    val hist = (live.history[pid.id] ?: emptyList()) + value.toFloat()
                    live.copy(
                        values = live.values + (pid.id to value),
                        history = live.history + (pid.id to hist.takeLast(HISTORY_SIZE)),
                    )
                }
            }
        }
    }

    fun readCodes() {
        val e = elm ?: run {
            _codes.value = _codes.value.copy(message = "Connect to your scanner first.")
            return
        }
        if (_codes.value.busy) return
        _codes.value = _codes.value.copy(busy = true, message = null)
        scope.launch {
            _codes.value = try {
                CodesState(report = e.readCodes())
            } catch (ex: Exception) {
                _codes.value.copy(busy = false, message = "Couldn't read codes: ${ex.message}")
            }
        }
    }

    fun clearCodes() {
        val e = elm ?: return
        if (_codes.value.busy) return
        _codes.value = _codes.value.copy(busy = true, message = null)
        scope.launch {
            _codes.value = try {
                val ok = e.clearCodes()
                delay(1000) // Give the ECU a moment before re-reading.
                CodesState(
                    report = e.readCodes(),
                    message = if (ok) "Codes cleared. If the problem is still there, they'll come back." else "The car didn't confirm the clear. Try with the engine off and ignition on.",
                )
            } catch (ex: Exception) {
                _codes.value.copy(busy = false, message = "Couldn't clear codes: ${ex.message}")
            }
        }
    }

    companion object {
        const val HISTORY_SIZE = 90
        private const val MAX_TIMEOUTS_IN_A_ROW = 6
        private const val MAX_RECONNECTS = 20
    }
}
