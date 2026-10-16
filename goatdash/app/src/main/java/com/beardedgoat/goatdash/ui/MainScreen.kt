package com.beardedgoat.goatdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.ui.theme.Danger
import com.beardedgoat.goatdash.ui.theme.Good
import com.beardedgoat.goatdash.ui.theme.Muted
import com.beardedgoat.goatdash.ui.theme.Warn

private enum class Tab(val label: String, val icon: ImageVector) {
    DASH("Dashboard", Icons.Filled.Speed),
    CODES("Codes", Icons.Filled.Warning),
    CUSTOMIZE("Customize", Icons.Filled.Tune),
    CONNECT("Connect", Icons.Filled.Bluetooth),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: GoatDashApp) {
    val state by app.obd.state.collectAsStateWithLifecycle()
    // First launch with nothing set up → start on the Connect tab.
    var tab by rememberSaveable { mutableIntStateOf(if (app.obd.lastTarget == null) Tab.CONNECT.ordinal else Tab.DASH.ordinal) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GoatDash", fontWeight = FontWeight.Black) },
                actions = { StatusPill(state, onClick = { tab = Tab.CONNECT.ordinal }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.DASH -> DashboardTab(app, onConnect = { tab = Tab.CONNECT.ordinal })
                Tab.CODES -> CodesTab(app, onConnect = { tab = Tab.CONNECT.ordinal })
                Tab.CUSTOMIZE -> CustomizeTab(app)
                Tab.CONNECT -> ConnectTab(app)
            }
        }
    }
}

@Composable
private fun StatusPill(state: ConnState, onClick: () -> Unit) {
    val (color, text) = when (state) {
        is ConnState.Connected -> Good to state.target.displayName
        is ConnState.Connecting -> Warn to "Connecting…"
        is ConnState.Failed -> Danger to "Not connected"
        ConnState.Disconnected -> Muted to "Not connected"
    }
    Row(
        Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
    }
}
