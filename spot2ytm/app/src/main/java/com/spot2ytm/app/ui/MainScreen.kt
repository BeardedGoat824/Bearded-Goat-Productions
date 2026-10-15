package com.spot2ytm.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spot2ytm.app.data.MatchStatus
import com.spot2ytm.app.data.Track
import com.spot2ytm.app.ui.theme.Danger
import com.spot2ytm.app.ui.theme.Muted
import com.spot2ytm.app.ui.theme.SpotifyGreen
import com.spot2ytm.app.ui.theme.Warn
import com.spot2ytm.app.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = SpotifyGreen)) { append("Spot") }
                            append("2")
                            withStyle(SpanStyle(color = YouTubeRed)) { append("YTM") }
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                actions = {
                    IconButton(onClick = { showLog = true }) { Icon(Icons.Filled.Terminal, "Activity log") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SpotifyStep(state, vm) }
            item { MatchHeader(state, vm) }
            if (state.tracks.isEmpty()) {
                item { EmptyTracks() }
            } else {
                itemsIndexed(state.tracks, key = { i, t -> "$i|${t.artist}|${t.title}" }) { index, track ->
                    TrackRow(index, track, canRemove = !state.isMatching) { vm.removeTrack(index) }
                }
            }
            item { ExportStep(state, vm, context) }
        }
    }

    if (showSettings) SettingsSheet(state, vm) { showSettings = false }
    if (showLog) LogSheet(state.log) { showLog = false }
}

@Composable
private fun StepCard(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun Status(line: StatusLine) {
    val color = when (line.tone) {
        Tone.NEUTRAL -> Muted
        Tone.OK -> SpotifyGreen
        Tone.WARN -> Warn
        Tone.ERROR -> Danger
    }
    Text(line.text, color = color, fontSize = 13.sp)
}

@Composable
private fun SpotifyStep(state: UiState, vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    StepCard("1. Pull Spotify playlist") {
        OutlinedTextField(
            value = state.input,
            onValueChange = vm::onInputChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("open.spotify.com/playlist/…") },
            trailingIcon = {
                if (state.input.isNotEmpty()) {
                    IconButton(onClick = { vm.onInputChange("") }) { Icon(Icons.Filled.Close, "Clear") }
                } else {
                    IconButton(onClick = { clipboard.getText()?.text?.let(vm::onInputChange) }) {
                        Icon(Icons.Filled.ContentPaste, "Paste")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.fetchPlaylist() }),
        )
        Button(
            onClick = vm::fetchPlaylist,
            enabled = !state.isFetching && state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color(0xFF05160B)),
        ) {
            if (state.isFetching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF05160B))
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("Fetch playlist")
        }
        Status(state.inputStatus)
    }
}

@Composable
private fun MatchHeader(state: UiState, vm: MainViewModel) {
    val total = state.tracks.size
    val processed = state.tracks.count { it.status == MatchStatus.MATCHED || it.status == MatchStatus.NOT_FOUND || it.status == MatchStatus.FAILED }
    StepCard(
        title = "2. Match on YouTube ($total)",
        trailing = {
            if (state.isMatching) {
                OutlinedButton(onClick = vm::cancelMatching) { Text("Stop") }
            } else {
                Button(onClick = vm::matchAll, enabled = total > 0) {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Match all")
                }
            }
        },
    ) {
        if (state.isMatching && total > 0) {
            LinearProgressIndicator(progress = { processed / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        Status(state.matchStatus)
    }
}

@Composable
private fun EmptyTracks() {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text("No tracks loaded yet.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun TrackRow(index: Int, track: Track, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${index + 1}. ${track.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotBlank()) {
                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (track.status) {
            MatchStatus.PENDING -> Text("Pending", color = Muted, fontSize = 12.sp)
            MatchStatus.SEARCHING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            MatchStatus.MATCHED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(track.videoId.orEmpty(), color = SpotifyGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            MatchStatus.NOT_FOUND -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Not found", color = Danger, fontSize = 12.sp)
            }
            MatchStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, null, tint = Warn, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", color = Warn, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, "Remove track", tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportStep(state: UiState, vm: MainViewModel, context: Context) {
    val clipboard = LocalClipboardManager.current
    StepCard("3. Export to YouTube Music") {
        Text("Instant queue (no Google login):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        val links = state.queueLinks
        if (links.isEmpty()) {
            Text("Queue links appear here once tracks are matched.", color = Muted, fontSize = 13.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEachIndexed { i, url ->
                    val count = minOf(50, state.matchedIds.size - i * 50)
                    AssistChip(
                        onClick = { context.openUrl(url) },
                        label = { Text("Queue part ${i + 1} ($count)") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)

        Text("Save permanently to your account:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FilledTonalButton(onClick = { context.openUrl(MainViewModel.OAUTH_PLAYGROUND_URL) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Key, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get token (Google OAuth Playground)")
        }
        OutlinedTextField(
            value = state.youtubeToken,
            onValueChange = vm::onYoutubeTokenChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Access token (ya29…)") },
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { clipboard.getText()?.text?.let(vm::onYoutubeTokenChange) }) {
                    Icon(Icons.Filled.ContentPaste, "Paste token")
                }
            },
        )
        OutlinedTextField(
            value = state.playlistTitle,
            onValueChange = vm::onPlaylistTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("New playlist name") },
        )
        Button(
            onClick = vm::createYouTubePlaylist,
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed, contentColor = Color.White),
        ) {
            if (state.isExporting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text("Create YouTube Music playlist")
        }
        Status(state.exportStatus)
        state.exportedPlaylistUrl?.let { url ->
            OutlinedButton(onClick = { context.openUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open in YouTube Music")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: UiState, vm: MainViewModel, onDismiss: () -> Unit) {
    var clientId by remember { mutableStateOf(state.clientId) }
    var clientSecret by remember { mutableStateOf(state.clientSecret) }
    var pasted by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Spotify API credentials", fontWeight = FontWeight.Bold)
            Text("Used for the official Web API. If they fail, the app falls back to the public embed page automatically.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Client ID") })
            OutlinedTextField(
                clientSecret, { clientSecret = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Client secret") }, visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveSpotifyCredentials(clientId, clientSecret) }) { Text("Save") }
                OutlinedButton(onClick = {
                    vm.saveSpotifyCredentials(clientId, clientSecret)
                    vm.testSpotifyCredentials()
                }) { Text("Save & test") }
            }
            Status(state.inputStatus)

            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline)

            Text("Paste a track list instead", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                pasted, { pasted = it }, Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("One per line:\nArtist - Song title") },
            )
            Button(onClick = {
                vm.loadPastedLines(pasted)
                onDismiss()
            }, enabled = pasted.isNotBlank()) { Text("Load pasted lines") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(log: List<String>, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Activity log", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(420.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            itemsIndexed(log) { _, line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}
