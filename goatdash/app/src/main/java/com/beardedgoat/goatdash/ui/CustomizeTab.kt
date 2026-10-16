package com.beardedgoat.goatdash.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.dash.DashPage
import com.beardedgoat.goatdash.dash.DashboardConfig
import com.beardedgoat.goatdash.dash.Gauge
import com.beardedgoat.goatdash.dash.GaugeColors
import com.beardedgoat.goatdash.dash.GaugeStyle
import com.beardedgoat.goatdash.dash.MusicButton
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.obd.LiveData
import com.beardedgoat.goatdash.obd.Pid
import com.beardedgoat.goatdash.obd.Pids
import com.beardedgoat.goatdash.ui.theme.Muted
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun CustomizeTab(app: GoatDashApp) {
    val config by app.dashboard.config.collectAsStateWithLifecycle()
    val current by app.dashboard.currentPage.collectAsStateWithLifecycle()
    val state by app.obd.state.collectAsStateWithLifecycle()
    val realLive by app.obd.live.collectAsStateWithLifecycle()
    val music by app.music.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val pageIndex = current.coerceIn(0, config.pages.lastIndex)
    val page = config.pages[pageIndex]
    val slot = selected.coerceIn(0, page.slots - 1)
    val gauge = page.gauges[slot]
    var testFlashUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(testFlashUntil) {
        if (testFlashUntil > 0) {
            delay(testFlashUntil - System.currentTimeMillis())
            testFlashUntil = 0
        }
    }
    // Without a car, show made-up readings so the preview still looks like something.
    val baseLive = if (state is ConnState.Connected) realLive else sampleLive
    // "Test flash" pushes the preview's RPM past every shift point on the page for a moment.
    val live = if (testFlashUntil > 0) {
        val peak = page.gauges.mapNotNull { it.shiftAt }.maxOrNull()
        if (peak != null) baseLive.copy(values = baseLive.values + ("RPM" to peak + 100)) else baseLive
    } else baseLive

    fun editGauge(transform: (Gauge) -> Gauge) = app.dashboard.updatePage(pageIndex) { p ->
        p.copy(gauges = p.gauges.mapIndexed { i, g -> if (i == slot) transform(g) else g })
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Pages
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            config.pages.forEachIndexed { i, p ->
                FilterChip(selected = i == pageIndex, onClick = { app.dashboard.showPage(i); selected = 0 }, label = { Text(p.name) })
            }
            if (config.pages.size < MAX_PAGES) {
                IconButton(onClick = {
                    app.dashboard.update { c -> c.copy(pages = c.pages + DashPage("Page ${c.pages.size + 1}", 2, 2, emptyList()).resized(2, 2)) }
                    app.dashboard.showPage(config.pages.size)
                    selected = 0
                }) { Icon(Icons.Filled.Add, contentDescription = "Add page") }
            }
        }

        // Car-shaped preview; tap a gauge to edit it.
        Text("Tap a gauge to edit it", style = MaterialTheme.typography.bodySmall, color = Muted)
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF07090D)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        ) {
            GaugePage(
                page, live, config.imperial, Modifier.fillMaxSize(), selectedSlot = slot, onSlotTap = { selected = it },
                music = music, musicButtons = config.musicButtons,
            )
        }

        // Selected gauge
        SectionCard("Gauge ${slot + 1}") {
            PidPicker(gauge.pidId) { id ->
                editGauge {
                    it.copy(pidId = id, warnAbove = Pids[id]?.defaultWarn, shiftAt = if (Gauge.supportsShiftLight(id)) it.shiftAt else null)
                }
            }

            if (gauge.isMusic) {
                Text("Shows what's playing with your music buttons. Pick the buttons under Music below.", style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                Label("Style")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GaugeStyle.entries.forEachIndexed { i, style ->
                        SegmentedButton(
                            selected = gauge.style == style,
                            onClick = { editGauge { it.copy(style = style) } },
                            shape = SegmentedButtonDefaults.itemShape(i, GaugeStyle.entries.size),
                        ) { Text(style.label) }
                    }
                }
            }

            Label("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GaugeColors.palette.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c)).clickable { editGauge { it.copy(color = c) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (gauge.color == c) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.Black)
                    }
                }
            }

            Pids[gauge.pidId]?.let { pid -> WarningEditor(pid, gauge.warnAbove, config.imperial) { w -> editGauge { it.copy(warnAbove = w) } } }

            if (Gauge.supportsShiftLight(gauge.pidId)) {
                ShiftLightEditor(
                    gauge,
                    onChange = { at, color -> editGauge { it.copy(shiftAt = at, shiftColor = color) } },
                    onTest = { testFlashUntil = System.currentTimeMillis() + 3000 },
                )
            }
        }

        // Page layout
        SectionCard("Page layout") {
            OutlinedTextField(
                value = page.name,
                onValueChange = { name -> app.dashboard.updatePage(pageIndex) { it.copy(name = name.take(20)) } },
                label = { Text("Page name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Columns", page.columns, 1, DashPage.MAX_COLUMNS) { c -> app.dashboard.updatePage(pageIndex) { it.resized(c, it.rows) } }
            Stepper("Rows", page.rows, 1, DashPage.MAX_ROWS) { r -> app.dashboard.updatePage(pageIndex) { it.resized(it.columns, r) } }
            if (config.pages.size > 1) {
                TextButton(onClick = {
                    app.dashboard.update { c -> c.copy(pages = c.pages.filterIndexed { i, _ -> i != pageIndex }) }
                    selected = 0
                }) { Text("Delete this page", color = MaterialTheme.colorScheme.error) }
            }
        }

        // Global
        SectionCard("Music") {
            MusicSettings(
                hasAccess = music.hasAccess,
                buttons = config.musicButtons,
                carButtons = config.carMusicButtons,
                onButtons = { b -> app.dashboard.update { it.copy(musicButtons = b) } },
                onCarButtons = { on -> app.dashboard.update { it.copy(carMusicButtons = on) } },
            )
        }

        SectionCard("Android Auto") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Full-screen gauges", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Gauges fill the whole car screen. GoatDash's buttons float on top and fade away when you're not touching the screen. " +
                            "Turn off to shrink the gauges so the buttons never cover them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                Switch(config.carFullScreen, onCheckedChange = { on -> app.dashboard.update { it.copy(carFullScreen = on) } })
            }
        }

        SectionCard("Units") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(config.imperial, { app.dashboard.update { it.copy(imperial = true) } }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("mph · °F · psi") }
                SegmentedButton(!config.imperial, { app.dashboard.update { it.copy(imperial = false) } }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("km/h · °C · kPa") }
            }
        }

        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset dashboard to default") }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset dashboard?") },
            text = { Text("This replaces all your pages and gauges with the default layout.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; app.dashboard.resetToDefault(); selected = 0 }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = Muted)

@Composable
private fun PidPicker(selectedId: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedId == Gauge.MUSIC) MUSIC_LABEL else Pids[selectedId]?.name ?: selectedId, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(MUSIC_LABEL) }, onClick = { open = false; onPick(Gauge.MUSIC) })
            HorizontalDivider()
            Pids.all.forEach { pid ->
                DropdownMenuItem(text = { Text(pid.name) }, onClick = { open = false; onPick(pid.id) })
            }
        }
    }
}

@Composable
private fun WarningEditor(pid: Pid, warnAbove: Double?, imperial: Boolean, onChange: (Double?) -> Unit) {
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Warning", fontWeight = FontWeight.SemiBold)
            Text(
                if (warnAbove == null) "Off" else "Turns red above ${pid.format(warnAbove, imperial)} ${pid.unit.label(imperial)}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Switch(checked = warnAbove != null, onCheckedChange = { on -> onChange(if (on) pid.defaultWarn ?: (pid.min + (pid.max - pid.min) * 0.8) else null) })
    }
    if (warnAbove != null) {
        Slider(
            value = warnAbove.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = pid.min.toFloat()..pid.max.toFloat(),
        )
    }
}

@Composable
private fun ShiftLightEditor(gauge: Gauge, onChange: (Double?, Int?) -> Unit, onTest: () -> Unit) {
    val pid = Pids[gauge.pidId] ?: return
    val shiftAt = gauge.shiftAt
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Shift light", fontWeight = FontWeight.SemiBold)
            Text(
                if (shiftAt == null) "Off" else "Gauge flashes at ${shiftAt.roundToInt()} rpm and above",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Switch(checked = shiftAt != null, onCheckedChange = { on -> onChange(if (on) DEFAULT_SHIFT_RPM else null, gauge.shiftColor) })
    }
    if (shiftAt == null) return

    Slider(
        value = shiftAt.toFloat(),
        // Snap to 100 rpm steps.
        onValueChange = { onChange((it / 100f).roundToInt() * 100.0, gauge.shiftColor) },
        valueRange = 1000f..pid.max.toFloat(),
    )
    Label("Flash color")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // "Auto" picks the colour opposite the gauge's own, so it always contrasts.
        val auto = GaugeColors.contrasting(gauge.color)
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(auto)).clickable { onChange(shiftAt, null) },
            contentAlignment = Alignment.Center,
        ) {
            if (gauge.shiftColor == null) Icon(Icons.Filled.Check, contentDescription = "Auto (selected)", tint = Color.Black)
            else Text("A", color = Color.Black, fontWeight = FontWeight.Black)
        }
        GaugeColors.shiftPalette.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c)).clickable { onChange(shiftAt, c) },
                contentAlignment = Alignment.Center,
            ) {
                if (gauge.shiftColor == c) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.Black)
            }
        }
    }
    Text("A = automatic: the opposite of the gauge's color.", style = MaterialTheme.typography.bodySmall, color = Muted)
    OutlinedButton(onClick = onTest) { Text("Test flash") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicSettings(
    hasAccess: Boolean,
    buttons: List<MusicButton>,
    carButtons: Boolean,
    onButtons: (List<MusicButton>) -> Unit,
    onCarButtons: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    if (!hasAccess) {
        Text(
            "To show and control what's playing (Spotify, YouTube Music, Pandora, podcasts…), Android needs you to give GoatDash " +
                "notification access. GoatDash only uses it to see the music player; it doesn't read your notifications.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Allow music access") }
        Text(
            "Greyed out with \"Controlled by restricted setting\"? Android blocks this for apps installed outside the Play Store. " +
                "Try once so Android shows that message, then go to Settings → Apps → GoatDash → ⋮ (top right) → " +
                "Allow restricted settings, confirm, and come back here.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
        }) { Text("Open GoatDash app info") }
    } else {
        Text("Music access is on.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }

    Label("Buttons")
    // Wrap onto a second line on narrow phones.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicButton.entries.forEach { b ->
            val on = b in buttons
            FilterChip(
                selected = on,
                // Keep at least one button, and keep them in their natural left-to-right order.
                onClick = { if (!on) onButtons((buttons + b).sorted()) else if (buttons.size > 1) onButtons(buttons - b) },
                label = { Text(b.label) },
            )
        }
    }
    Text("Add a Music controls tile to any page: tap a gauge above and pick Music controls.", style = MaterialTheme.typography.bodySmall, color = Muted)

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Music buttons on the car screen", fontWeight = FontWeight.SemiBold)
            Text(
                "Adds them as a column of buttons on the Android Auto screen too" +
                    if (buttons.size > DashboardConfig.MAX_CAR_MUSIC_BUTTONS) " (it fits 4, so Back 15s is left off)." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Switch(carButtons, onCheckedChange = onCarButtons)
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) { Icon(Icons.Filled.Remove, contentDescription = "Fewer $label") }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Icon(Icons.Filled.Add, contentDescription = "More $label") }
    }
}

private const val MAX_PAGES = 6
private const val MUSIC_LABEL = "Music controls"
private const val DEFAULT_SHIFT_RPM = 5500.0

private val sampleLive: LiveData = LiveData(
    values = Pids.all.associate { it.id to it.min + (it.max - it.min) * 0.55 },
    history = Pids.all.associate { pid ->
        pid.id to List(60) { i -> (pid.min + (pid.max - pid.min) * (0.5 + 0.3 * sin(i / 6.0))).toFloat() }
    },
)
