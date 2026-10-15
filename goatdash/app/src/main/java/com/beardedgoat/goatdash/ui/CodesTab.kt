package com.beardedgoat.goatdash.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.obd.DtcDescriptions
import com.beardedgoat.goatdash.obd.DtcReport
import com.beardedgoat.goatdash.ui.theme.Danger
import com.beardedgoat.goatdash.ui.theme.Good
import com.beardedgoat.goatdash.ui.theme.Muted
import com.beardedgoat.goatdash.ui.theme.Warn
import java.text.DateFormat
import java.util.Date

@Composable
fun CodesTab(app: GoatDashApp, onConnect: () -> Unit) {
    val codes by app.obd.codes.collectAsStateWithLifecycle()
    val state by app.obd.state.collectAsStateWithLifecycle()
    val connected = state is ConnState.Connected
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val report = codes.report

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (connected) app.obd.readCodes() else onConnect() }, enabled = !codes.busy, modifier = Modifier.weight(1f)) {
                    Text(if (connected) "Read codes" else "Connect first")
                }
                OutlinedButton(onClick = { confirmClear = true }, enabled = connected && !codes.busy, modifier = Modifier.weight(1f)) {
                    Text("Clear codes")
                }
            }
        }
        if (codes.busy) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Asking the car…", color = Muted)
            }
        }
        codes.message?.let { msg -> item { Text(msg, color = Warn) } }

        if (report == null && !codes.busy) item {
            Text(
                "Tap Read codes to check for trouble codes — the same thing a shop's scan tool or an auto parts store does when the check engine light comes on.",
                color = Muted,
            )
        }

        if (report != null) {
            item { SummaryCard(report, onShare = { shareReport(context, report) }) }
            sectionItems("Stored codes", "Confirmed problems. These usually turn on the check engine light.", report.stored, Danger)
            sectionItems("Pending codes", "Seen once. If it happens again on the next drive it becomes a stored code.", report.pending, Warn)
            sectionItems("Permanent codes", "Can't be cleared with a scanner. They go away on their own after the car confirms the fix.", report.permanent, Muted)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all codes?") },
            text = {
                Text(
                    "This turns off the check engine light and erases the codes and freeze-frame data.\n\n" +
                        "Heads up: it also resets your car's emissions readiness monitors, so don't do it right before an inspection — " +
                        "the car needs a few days of normal driving to be ready again. Clearing doesn't fix anything; if the problem is real, the code will come back.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; app.obd.clearCodes() }) { Text("Clear codes", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionItems(title: String, help: String, codes: List<String>, color: Color) {
    if (codes.isEmpty()) return
    item {
        Column(Modifier.padding(top = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(help, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    items(codes) { code -> CodeCard(code, color) }
}

@Composable
private fun SummaryCard(report: DtcReport, onShare: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (report.milOn) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (report.milOn) Warn else Good,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (report.milOn) "Check engine light is ON" else "Check engine light is off",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (report.isEmpty) "No trouble codes found." else "${report.stored.size} stored · ${report.pending.size} pending · ${report.permanent.size} permanent",
                    color = Muted,
                )
                Text("Scanned ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(report.readAtMillis))}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share report") }
        }
    }
}

@Composable
private fun CodeCard(code: String, color: Color) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(code, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(DtcDescriptions.describe(code), modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode("$code OBD2 code meaning"))
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Look up $code online")
            }
        }
    }
}

private fun shareReport(context: android.content.Context, report: DtcReport) {
    val text = buildString {
        appendLine("GoatDash scan — ${DateFormat.getDateTimeInstance().format(Date(report.readAtMillis))}")
        appendLine("Check engine light: ${if (report.milOn) "ON" else "off"}")
        fun section(name: String, list: List<String>) {
            if (list.isEmpty()) return
            appendLine()
            appendLine("$name:")
            list.forEach { appendLine("  $it — ${DtcDescriptions.describe(it)}") }
        }
        section("Stored", report.stored)
        section("Pending", report.pending)
        section("Permanent", report.permanent)
        if (report.isEmpty) appendLine("No trouble codes found.")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share scan report"))
}
