package com.beardedgoat.goatdash.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.Permissions
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.obd.ConnectionTarget
import com.beardedgoat.goatdash.ui.theme.Danger
import com.beardedgoat.goatdash.ui.theme.Good
import com.beardedgoat.goatdash.ui.theme.Muted
import kotlinx.coroutines.delay

/** Names that usually mean "this Bluetooth device is an OBD2 adapter". */
private val obdNameHints = listOf("OBD", "ELM", "VGATE", "VEEPEAK", "V-LINK", "VLINK", "KONNWEI", "BAFX", "CARISTA", "IOS-VLINK", "ICAR", "SCAN")

private fun looksLikeObd(name: String) = obdNameHints.any { name.uppercase().contains(it) }

@Composable
fun ConnectTab(app: GoatDashApp) {
    val context = LocalContext.current
    val state by app.obd.state.collectAsStateWithLifecycle()
    var hasBt by remember { mutableStateOf(Permissions.hasBluetooth(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBt = Permissions.hasBluetooth(context)
    }
    var autoConnect by remember { mutableStateOf(app.obd.autoConnect) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusCard(state, onDisconnect = { app.obd.disconnect() }, onRetry = { app.obd.connect(it) })

        if (!hasBt) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bluetooth permission needed", fontWeight = FontWeight.Bold)
                    Text("GoatDash needs to see nearby Bluetooth devices to find your OBD2 scanner. (Not needed for Wi-Fi scanners or demo mode.)", color = Muted)
                    Button(onClick = { permissionLauncher.launch(Permissions.all) }) { Text("Allow Bluetooth") }
                }
            }
        } else {
            PairedDevices(context) { app.obd.connect(it) }
            BleScanner(context) { app.obd.connect(it) }
        }

        WifiCard { app.obd.connect(it) }

        Section("No car handy?", Icons.Filled.DirectionsCar) {
            Text("Demo mode pretends to be a car on a short drive, with a couple of trouble codes. Great for setting up your dashboard.", color = Muted)
            OutlinedButton(onClick = { app.obd.connect(ConnectionTarget.Demo) }) { Text("Start demo mode") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-connect", fontWeight = FontWeight.SemiBold)
                Text("Reconnect to the last scanner when the app opens or Android Auto starts.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(autoConnect, onCheckedChange = { autoConnect = it; app.obd.autoConnect = it })
        }

        AndroidAutoHelp()
    }
}

@Composable
private fun StatusCard(state: ConnState, onDisconnect: () -> Unit, onRetry: (ConnectionTarget) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (state) {
                ConnState.Disconnected -> {
                    Text("Not connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Plug your scanner into the OBD2 port (under the dash, near your knees), turn the key to ON, then pick it below.", color = Muted)
                }
                is ConnState.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(state.step, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(state.target.displayName, color = Muted)
                    TextButton(onClick = onDisconnect) { Text("Cancel") }
                }
                is ConnState.Connected -> {
                    Text("Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Good)
                    InfoLine("Scanner", state.target.displayName)
                    InfoLine("Adapter", state.info.version)
                    InfoLine("Protocol", state.info.protocol)
                    state.info.vin?.let { InfoLine("VIN", it) }
                    if (state.info.supportedPids.isNotEmpty()) InfoLine("Sensors", "${state.info.supportedPids.size} reported by the car")
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                }
                is ConnState.Failed -> {
                    Text("Couldn't connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Danger)
                    Text(state.message)
                    Text(
                        "Tips: make sure the ignition is ON, the scanner's light is on, and no other app (like Torque or Car Scanner) is connected to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Button(onClick = { onRetry(state.target) }) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, color = Muted, modifier = Modifier.width(80.dp))
        Text(value)
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun DeviceRow(name: String, detail: String, highlight: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (highlight) Text("Looks like OBD2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@SuppressLint("MissingPermission") // Only shown once Bluetooth permission is granted.
@Composable
private fun PairedDevices(context: Context, onPick: (ConnectionTarget) -> Unit) {
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    Section("Bluetooth scanners", Icons.Filled.Bluetooth) {
        if (adapter == null) {
            Text("This phone doesn't have Bluetooth.", color = Muted)
            return@Section
        }
        if (!adapter.isEnabled) {
            Text("Bluetooth is off.", color = Muted)
            return@Section
        }
        val devices = remember(adapter) {
            adapter.bondedDevices.orEmpty()
                .map { (it.name ?: it.address) to it.address }
                .sortedByDescending { looksLikeObd(it.first) }
        }
        Text("Paired devices — pair your scanner in Android's Bluetooth settings first (the PIN is usually 1234 or 0000).", style = MaterialTheme.typography.bodySmall, color = Muted)
        devices.forEachIndexed { i, (name, address) ->
            if (i > 0) HorizontalDivider()
            DeviceRow(name, address, looksLikeObd(name)) { onPick(ConnectionTarget.Classic(address, name)) }
        }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Open Bluetooth settings") }
    }
}

@SuppressLint("MissingPermission") // Only shown once Bluetooth permission is granted.
@Composable
private fun BleScanner(context: Context, onPick: (ConnectionTarget) -> Unit) {
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var scanning by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Pair<String, String>>() }
    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                if (found.none { it.second == result.device.address }) found += name to result.device.address
            }
        }
    }
    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        found.clear()
        val scanner = adapter?.bluetoothLeScanner ?: run { scanning = false; return@LaunchedEffect }
        try {
            scanner.startScan(callback)
            delay(10_000)
        } finally {
            // Also runs when a device is picked or the screen closes mid-scan.
            runCatching { scanner.stopScan(callback) }
            scanning = false
        }
    }
    if (adapter == null || !adapter.isEnabled) return

    Section("Bluetooth LE scanners", Icons.Filled.BluetoothSearching) {
        Text("Newer scanners (Vgate iCar Pro BLE, OBDLink CX, Veepeak BLE…) don't pair in settings — scan for them here.", style = MaterialTheme.typography.bodySmall, color = Muted)
        found.sortedByDescending { looksLikeObd(it.first) }.forEachIndexed { i, (name, address) ->
            if (i > 0) HorizontalDivider()
            DeviceRow(name, address, looksLikeObd(name)) {
                scanning = false
                onPick(ConnectionTarget.Ble(address, name))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { scanning = true }, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan") }
            if (scanning) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun WifiCard(onConnect: (ConnectionTarget) -> Unit) {
    var host by remember { mutableStateOf("192.168.0.10") }
    var port by remember { mutableStateOf("35000") }
    Section("Wi-Fi scanners", Icons.Filled.Wifi) {
        Text("Join the scanner's Wi-Fi network in Android settings first (it's usually named WiFi_OBDII or similar).", style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it.trim() }, label = { Text("Address") }, singleLine = true, modifier = Modifier.weight(2f))
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(onClick = { port.toIntOrNull()?.let { onConnect(ConnectionTarget.Wifi(host, it)) } }, enabled = host.isNotBlank() && port.isNotBlank()) {
            Text("Connect over Wi-Fi")
        }
    }
}

@Composable
private fun AndroidAutoHelp() {
    Section("Showing it in Android Auto", Icons.Filled.DirectionsCar) {
        Text(
            "Android Auto only shows dashboard apps like GoatDash when they're installed from Google Play. " +
                "The \"Unknown sources\" developer setting doesn't cover this kind of app, so a downloaded APK works on your phone " +
                "but won't appear in the car's app list.\n\n" +
                "To use it in the car, install GoatDash through a Google Play internal testing link (a private, unreviewed " +
                "Play Store listing just for you). Once it's installed that way, check Android Auto settings → Customize launcher " +
                "and make sure GoatDash is ticked.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
