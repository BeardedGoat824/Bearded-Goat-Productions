package com.beardedgoat.goatdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.obd.ConnState
import com.beardedgoat.goatdash.ui.theme.Border
import com.beardedgoat.goatdash.ui.theme.Muted

@Composable
fun DashboardTab(app: GoatDashApp, onConnect: () -> Unit) {
    val config by app.dashboard.config.collectAsStateWithLifecycle()
    val current by app.dashboard.currentPage.collectAsStateWithLifecycle()
    val live by app.obd.live.collectAsStateWithLifecycle()
    val state by app.obd.state.collectAsStateWithLifecycle()

    // Keep the screen awake while the gauges are showing.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val pager = rememberPagerState(initialPage = current) { config.pages.size }
    // Two-way sync: swiping here changes the car screen's page, and vice versa.
    LaunchedEffect(pager.settledPage) { app.dashboard.showPage(pager.settledPage) }
    LaunchedEffect(current) { if (pager.currentPage != current) pager.animateScrollToPage(current) }

    Column(Modifier.fillMaxSize()) {
        if (state !is ConnState.Connected) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state is ConnState.Connecting) "Connecting…" else "Not connected — gauges will show --",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onConnect) { Text("Connect") }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            config.pages.getOrNull(index)?.let { page ->
                GaugePage(page, live, config.imperial, Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(config.pages.getOrNull(pager.currentPage)?.name.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Muted)
            Box(Modifier.size(12.dp))
            config.pages.indices.forEach { i ->
                Box(
                    Modifier.padding(3.dp).size(7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else Border),
                )
            }
        }
    }
}
