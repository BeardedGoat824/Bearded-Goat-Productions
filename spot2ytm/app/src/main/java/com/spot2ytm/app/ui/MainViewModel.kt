package com.spot2ytm.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spot2ytm.app.data.GoogleApiError
import com.spot2ytm.app.data.HttpException
import com.spot2ytm.app.data.MatchStatus
import com.spot2ytm.app.data.Settings
import com.spot2ytm.app.data.SpotifyLinks
import com.spot2ytm.app.data.SpotifyRepository
import com.spot2ytm.app.data.Track
import com.spot2ytm.app.data.YouTubeRepository
import com.spot2ytm.app.data.Http
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tone { NEUTRAL, OK, WARN, ERROR }

data class StatusLine(val text: String, val tone: Tone = Tone.NEUTRAL)

/** A playlist already created on YouTube and the songs already handled, so a failed export can resume. */
data class ExportProgress(val playlistId: String, val done: Set<String> = emptySet())

data class UiState(
    val input: String = "",
    val inputStatus: StatusLine = StatusLine("Paste a Spotify playlist link, or share one from the Spotify app."),
    val isFetching: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val isMatching: Boolean = false,
    val matchStatus: StatusLine = StatusLine("Fetch a playlist first."),
    val playlistTitle: String = "Imported from Spotify",
    val youtubeToken: String = "",
    val isExporting: Boolean = false,
    val exportStatus: StatusLine = StatusLine("Needs a Google access token starting with \"ya29.\"."),
    val exportedPlaylistUrl: String? = null,
    val exportProgress: ExportProgress? = null,
    val clientId: String = "",
    val clientSecret: String = "",
    val log: List<String> = listOf("Ready."),
) {
    val matchedIds: List<String> get() = tracks.mapNotNull { it.videoId }
    val queueLinks: List<String> get() = YouTubeRepository.queueLinks(matchedIds)
    /** Songs still to add when resuming an export (0 when there is nothing to resume). */
    val exportRemaining: Int get() = exportProgress?.let { p -> matchedIds.count { it !in p.done } } ?: 0
    val detectedPlaylistId: String? get() = SpotifyLinks.extractPlaylistId(input)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val spotify = SpotifyRepository()
    private val youtube = YouTubeRepository()

    private val _state = MutableStateFlow(
        UiState(
            clientId = settings.spotifyClientId,
            clientSecret = settings.spotifyClientSecret,
            youtubeToken = settings.youtubeToken,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var matchJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(message: String) = _state.update {
        it.copy(log = (it.log + "[${timeFormat.format(Date())}] $message").takeLast(300))
    }

    // ---- Step 1: Spotify ----

    fun onInputChange(value: String) {
        val id = SpotifyLinks.extractPlaylistId(value)
        val status = when {
            id != null -> StatusLine("Detected playlist ID: $id", Tone.OK)
            SpotifyLinks.findShortLink(value) != null -> StatusLine("Short link detected — tap Fetch to expand it.", Tone.OK)
            value.isNotBlank() -> StatusLine("No 22-character Spotify playlist ID found in that text.", Tone.WARN)
            else -> UiState().inputStatus
        }
        _state.update { it.copy(input = value, inputStatus = status) }
    }

    fun onSharedText(text: String) {
        log("Received shared text from Android: $text")
        onInputChange(SpotifyLinks.extractLink(text))
        if (state.value.detectedPlaylistId != null || SpotifyLinks.findShortLink(text) != null) fetchPlaylist()
    }

    fun fetchPlaylist() {
        if (state.value.isFetching) return
        viewModelScope.launch {
            _state.update { it.copy(isFetching = true, inputStatus = StatusLine("Fetching playlist…", Tone.WARN)) }
            try {
                var input = state.value.input
                SpotifyLinks.findShortLink(input)?.let { short ->
                    log("Expanding short link $short")
                    input = Http.resolveFinalUrl(short)
                    _state.update { it.copy(input = input) }
                }
                val id = SpotifyLinks.extractPlaylistId(input)
                if (id == null) {
                    _state.update { it.copy(inputStatus = StatusLine("Paste a valid Spotify playlist link first.", Tone.ERROR)) }
                    return@launch
                }
                log("Fetching playlist $id")

                val (playlist, source) = try {
                    val s = state.value
                    spotify.fetchViaApi(id, s.clientId, s.clientSecret).also {
                        if (it.tracks.isEmpty()) error("API returned no tracks")
                    } to "Spotify Web API"
                } catch (e: Exception) {
                    log("Web API failed (${e.describe()}); falling back to the public embed page.")
                    spotify.fetchViaEmbed(id) to "embed page"
                }

                if (playlist.tracks.isEmpty()) {
                    _state.update { it.copy(inputStatus = StatusLine("Playlist was empty or private. Make sure it's public.", Tone.ERROR)) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        tracks = playlist.tracks,
                        playlistTitle = playlist.name ?: it.playlistTitle,
                        inputStatus = StatusLine("Loaded ${playlist.name?.let { n -> "\"$n\" " } ?: ""}(${playlist.tracks.size} tracks) via $source.", Tone.OK),
                        matchStatus = StatusLine("${playlist.tracks.size} tracks ready to match."),
                        exportedPlaylistUrl = null,
                        exportProgress = null,
                    )
                }
                log("Loaded ${playlist.tracks.size} tracks via $source.")
            } catch (e: Exception) {
                log("Fetch failed: ${e.describe()}")
                _state.update { it.copy(inputStatus = StatusLine("Couldn't pull that playlist (${e.describe()}). Is it public?", Tone.ERROR)) }
            } finally {
                _state.update { it.copy(isFetching = false) }
            }
        }
    }

    fun loadPastedLines(raw: String) {
        val tracks = SpotifyLinks.parseRawLines(raw)
        if (tracks.isEmpty()) return
        _state.update {
            it.copy(
                tracks = tracks,
                inputStatus = StatusLine("Loaded ${tracks.size} tracks from pasted text.", Tone.OK),
                matchStatus = StatusLine("${tracks.size} tracks ready to match."),
                exportedPlaylistUrl = null,
                exportProgress = null,
            )
        }
        log("Loaded ${tracks.size} tracks from pasted text.")
    }

    fun saveSpotifyCredentials(clientId: String, clientSecret: String) {
        settings.spotifyClientId = clientId.trim()
        settings.spotifyClientSecret = clientSecret.trim()
        _state.update { it.copy(clientId = clientId.trim(), clientSecret = clientSecret.trim()) }
    }

    fun testSpotifyCredentials() {
        viewModelScope.launch {
            try {
                log("Testing Spotify client credentials…")
                spotify.accessToken(state.value.clientId, state.value.clientSecret)
                _state.update { it.copy(inputStatus = StatusLine("Spotify Client ID & Secret verified!", Tone.OK)) }
                log("Spotify token acquired.")
            } catch (e: Exception) {
                _state.update { it.copy(inputStatus = StatusLine("Spotify auth failed: ${e.describe()}", Tone.ERROR)) }
                log("Spotify auth failed: ${e.describe()}")
            }
        }
    }

    // ---- Step 2: Matching ----

    fun matchAll() {
        if (state.value.isMatching || state.value.tracks.isEmpty()) return
        matchJob = viewModelScope.launch {
            _state.update { it.copy(isMatching = true) }
            val total = state.value.tracks.size
            var done = 0
            try {
                coroutineScope {
                    val gate = Semaphore(MATCH_CONCURRENCY)
                    state.value.tracks.forEachIndexed { index, track ->
                        if (track.videoId != null) {
                            done++
                            return@forEachIndexed
                        }
                        launch {
                            gate.withPermit {
                                updateTrack(index) { it.copy(status = MatchStatus.SEARCHING) }
                                val (videoId, status) = when (val result = youtube.search(track)) {
                                    is YouTubeRepository.SearchResult.Found -> {
                                        log("Matched [${result.videoId}] ← ${track.searchQuery}")
                                        result.videoId to MatchStatus.MATCHED
                                    }
                                    YouTubeRepository.SearchResult.NotFound -> {
                                        log("No match for: ${track.searchQuery}")
                                        null to MatchStatus.NOT_FOUND
                                    }
                                    is YouTubeRepository.SearchResult.Failed -> {
                                        log("Couldn't search \"${track.searchQuery}\": ${result.reason}")
                                        null to MatchStatus.FAILED
                                    }
                                }
                                updateTrack(index) { it.copy(videoId = videoId, status = status) }
                                done++
                                _state.update { it.copy(matchStatus = StatusLine("Matching… $done of $total", Tone.WARN)) }
                            }
                        }
                    }
                }
                val matched = state.value.matchedIds.size
                val failed = state.value.tracks.count { it.status == MatchStatus.FAILED }
                val summary = if (failed > 0) {
                    StatusLine("Matched $matched of $total. $failed couldn't reach YouTube — tap Match all again to retry them.", Tone.WARN)
                } else {
                    StatusLine("Finished! Matched $matched of $total tracks.", Tone.OK)
                }
                _state.update { it.copy(matchStatus = summary) }
            } finally {
                // Covers cancellation too: anything left mid-search goes back to pending.
                _state.update { s ->
                    s.copy(
                        isMatching = false,
                        tracks = s.tracks.map { if (it.status == MatchStatus.SEARCHING) it.copy(status = MatchStatus.PENDING) else it },
                    )
                }
            }
        }
    }

    fun cancelMatching() {
        matchJob?.cancel()
        _state.update { it.copy(matchStatus = StatusLine("Stopped. Matched ${it.matchedIds.size} of ${it.tracks.size}.", Tone.WARN)) }
    }

    fun removeTrack(index: Int) = _state.update { s ->
        s.copy(tracks = s.tracks.filterIndexed { i, _ -> i != index })
    }

    private fun updateTrack(index: Int, transform: (Track) -> Track) = _state.update { s ->
        if (index !in s.tracks.indices) s
        else s.copy(tracks = s.tracks.toMutableList().also { it[index] = transform(it[index]) })
    }

    // ---- Step 3: Export ----

    fun onPlaylistTitleChange(value: String) = _state.update { it.copy(playlistTitle = value) }

    fun onYoutubeTokenChange(raw: String) {
        val token = TOKEN_PATTERN.find(raw)?.value ?: raw.trim()
        val status = when {
            token.startsWith("ya29.") -> StatusLine("Access token looks valid.", Tone.OK)
            token.startsWith("4/") -> StatusLine(
                "That's an authorization code (4/…). In OAuth Playground, tap \"Exchange authorization code for tokens\" and copy the ya29… access token.",
                Tone.ERROR,
            )
            token.isEmpty() -> UiState().exportStatus
            else -> StatusLine("Access tokens start with \"ya29.\".", Tone.WARN)
        }
        settings.youtubeToken = token
        _state.update { it.copy(youtubeToken = token, exportStatus = status) }
    }

    fun createYouTubePlaylist() {
        val s = state.value
        if (s.isExporting) return
        if (!s.youtubeToken.startsWith("ya29.")) {
            _state.update { it.copy(exportStatus = StatusLine("Paste a valid ya29… access token first.", Tone.ERROR)) }
            return
        }
        val ids = s.matchedIds
        if (ids.isEmpty()) {
            _state.update { it.copy(exportStatus = StatusLine("Match your tracks in step 2 first.", Tone.ERROR)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportedPlaylistUrl = null) }
            try {
                // Resume into the playlist made last time, or create a new one.
                val progress = s.exportProgress ?: run {
                    _state.update { it.copy(exportStatus = StatusLine("Creating playlist…", Tone.WARN)) }
                    val id = youtube.createPlaylist(s.youtubeToken, s.playlistTitle.ifBlank { "Spotify Import" })
                    log("Created YouTube playlist $id")
                    ExportProgress(id).also { p -> _state.update { it.copy(exportProgress = p) } }
                }
                val todo = ids.filter { it !in progress.done }
                var added = 0
                var skipped = 0
                todo.forEachIndexed { i, videoId ->
                    _state.update { it.copy(exportStatus = StatusLine("Adding songs… ${i + 1} of ${todo.size}", Tone.WARN)) }
                    try {
                        youtube.addToPlaylist(s.youtubeToken, progress.playlistId, videoId)
                        added++
                    } catch (e: GoogleApiError) {
                        if (e.stopsWholeExport) throw e
                        skipped++
                        log("Skipped video $videoId (${e.message})")
                    }
                    _state.update { st -> st.copy(exportProgress = st.exportProgress?.let { it.copy(done = it.done + videoId) }) }
                    delay(300) // stay well under YouTube's write rate limits
                }
                val skippedNote = if (skipped > 0) " $skipped couldn't be added (see log)." else ""
                _state.update {
                    it.copy(
                        exportStatus = StatusLine("Done! Added $added songs.$skippedNote", Tone.OK),
                        exportedPlaylistUrl = youtube.playlistMusicUrl(progress.playlistId),
                        exportProgress = null,
                    )
                }
            } catch (e: GoogleApiError) {
                log("Export failed: ${e.message}")
                val resumeNote = state.value.exportProgress?.let { p ->
                    val left = state.value.exportRemaining
                    if (left > 0) " The playlist was created and ${p.done.size} songs are in it — fix this, then tap Resume to add the other $left." else ""
                } ?: ""
                _state.update { it.copy(exportStatus = StatusLine(e.explanation + resumeNote, Tone.ERROR)) }
            } catch (e: Exception) {
                log("Export failed: ${e.describe()}")
                _state.update { it.copy(exportStatus = StatusLine("Couldn't reach YouTube (${e.describe()}). Check your connection and tap the button again.", Tone.ERROR)) }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    /** Forget a half-finished export so the next tap creates a brand-new playlist. */
    fun startNewExport() = _state.update {
        it.copy(exportProgress = null, exportStatus = StatusLine("The next export will create a new playlist."))
    }

    private fun Exception.describe(): String = when (this) {
        is HttpException -> "HTTP $code"
        else -> message ?: javaClass.simpleName
    }

    companion object {
        private const val MATCH_CONCURRENCY = 2
        private val TOKEN_PATTERN = Regex("""ya29\.[a-zA-Z0-9_\-]+""")
        const val OAUTH_PLAYGROUND_URL =
            "https://developers.google.com/oauthplayground/#step1&apisSelect=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fyoutube" +
                "&url=https%3A%2F%2F&content_type=application%2Fjson&http_method=GET&useDefaultOauthCred=unchecked"
    }
}
