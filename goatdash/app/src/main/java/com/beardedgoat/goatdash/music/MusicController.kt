package com.beardedgoat.goatdash.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.beardedgoat.goatdash.dash.MusicButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val appName: String?,
    val isPlaying: Boolean,
    val art: Bitmap?,
)

/** [hasAccess] is false until the user grants GoatDash notification access in Android settings. */
data class MusicState(
    val hasAccess: Boolean = false,
    val nowPlaying: NowPlaying? = null,
)

/**
 * Watches whatever app is playing music (Spotify, YouTube Music, Pandora, podcasts…) through
 * Android's media sessions and sends it play/pause/skip commands. Android only shares other
 * apps' media sessions with apps the user has granted notification access, which is what
 * [MediaListenerService] is for.
 */
class MusicController(private val context: Context) {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    fun hasAccess() = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Starts watching if access has been granted. Safe to call repeatedly (e.g. on every resume). */
    fun start() {
        if (!hasAccess()) {
            _state.value = MusicState(hasAccess = false)
            return
        }
        try {
            if (!listening) {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
                listening = true
            }
            refresh()
        } catch (_: SecurityException) {
            _state.value = MusicState(hasAccess = false)
        }
    }

    private fun refresh() {
        val list = try {
            sessions.getActiveSessions(listener)
        } catch (_: SecurityException) {
            _state.value = MusicState(hasAccess = false)
            return
        }
        pick(list)
    }

    /** Follows the session that's playing, or else the most recent one. */
    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = MusicState(hasAccess = true)
            return
        }
        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val previous = _state.value.nowPlaying
        _state.value = MusicState(
            hasAccess = true,
            nowPlaying = NowPlaying(
                title = title,
                artist = artist,
                appName = appName(c.packageName),
                isPlaying = c.isPlaying(),
                // Shrink big covers once here rather than on every frame we draw.
                art = if (previous?.title == title && previous?.artist == artist && previous?.art != null) previous.art else art?.let(::shrink),
            ),
        )
    }

    fun press(button: MusicButton) {
        val c = controller ?: return
        val controls = c.transportControls
        when (button) {
            MusicButton.PLAY_PAUSE -> if (c.isPlaying()) controls.pause() else controls.play()
            MusicButton.PREVIOUS -> controls.skipToPrevious()
            MusicButton.NEXT -> controls.skipToNext()
            MusicButton.BACK_15 -> seekBy(c, -15_000)
            MusicButton.FORWARD_30 -> seekBy(c, 30_000)
        }
    }

    private fun seekBy(c: MediaController, deltaMs: Long) {
        val playback = c.playbackState ?: return
        // The reported position is from the last update; add the time it has played since.
        val elapsed = if (playback.state == PlaybackState.STATE_PLAYING)
            ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong() else 0L
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: Long.MAX_VALUE
        c.transportControls.seekTo((playback.position + elapsed + deltaMs).coerceIn(0L, duration))
    }

    private fun MediaController.isPlaying() =
        playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }

    private fun appName(pkg: String): String? = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun shrink(b: Bitmap): Bitmap {
        val max = 256
        if (b.width <= max && b.height <= max) return b
        val scale = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }
}
