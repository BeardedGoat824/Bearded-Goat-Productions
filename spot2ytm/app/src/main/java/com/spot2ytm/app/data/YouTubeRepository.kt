package com.spot2ytm.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class YouTubeRepository {
    private val jsonType = "application/json".toMediaType()
    private val videoIdPattern = Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""")
    private val webVideoPattern = Regex(""""videoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"""")
    private val visitorPattern = Regex(""""visitorData":"([^"]+)"""")

    /** Spaces requests out and pauses everyone when YouTube says "slow down". */
    private val throttle = Mutex()
    private var lastRequestAt = 0L
    @Volatile private var pausedUntil = 0L

    /** YouTube's visitor ID, picked up from the first response and sent back like a browser does. */
    @Volatile private var visitorData: String? = null

    /** The "Songs" filter returns nothing in some regions; stop wasting a request on it if so. */
    @Volatile private var songsFilterEmptyStreak = 0

    sealed interface SearchResult {
        data class Found(val videoId: String) : SearchResult
        data object NotFound : SearchResult
        data class Failed(val reason: String) : SearchResult
    }

    /**
     * Finds a video for the track without any API key or quota. Tries the full "artist title" query and,
     * if that finds nothing, a cleaned-up one (first artist, no "feat."/"From …" suffixes). Each query goes
     * through YouTube Music "Songs" search, plain YouTube Music search, then regular YouTube search.
     */
    suspend fun search(track: Track): SearchResult {
        val queries = listOf(track.searchQuery, simplifiedQuery(track)).filter { it.isNotBlank() }.distinct()
        var failure: String? = null
        var gotCleanAnswer = false
        for (query in queries) {
            val strategies = buildList {
                if (songsFilterEmptyStreak < SONGS_FILTER_GIVE_UP) add(Strategy.SONGS)
                add(Strategy.MUSIC)
                add(Strategy.WEB)
            }
            for (strategy in strategies) {
                try {
                    val id = withRetries { run(strategy, query) }
                    gotCleanAnswer = true
                    if (strategy == Strategy.SONGS) songsFilterEmptyStreak = if (id == null) songsFilterEmptyStreak + 1 else 0
                    if (id != null) return SearchResult.Found(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = describe(e)
                }
            }
        }
        return if (gotCleanAnswer || failure == null) SearchResult.NotFound else SearchResult.Failed(failure)
    }

    private enum class Strategy { SONGS, MUSIC, WEB }

    private suspend fun run(strategy: Strategy, query: String): String? = when (strategy) {
        Strategy.SONGS -> musicSearch(query, SONGS_FILTER)
        Strategy.MUSIC -> musicSearch(query, null)
        Strategy.WEB -> webSearch(query)
    }

    /** Retries rate-limit, server and network errors with growing pauses; other errors fail at once. */
    private suspend fun <T> withRetries(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            awaitTurn()
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val retryable = e !is HttpException || e.code == 429 || e.code >= 500
                if (e is HttpException && e.code == 429) {
                    pausedUntil = maxOf(pausedUntil, System.currentTimeMillis() + RATE_LIMIT_PAUSE_MS)
                }
                if (!retryable || attempt >= MAX_RETRIES) throw e
                delay(RETRY_BACKOFF_MS * (1L shl attempt))
                attempt++
            }
        }
    }

    private suspend fun awaitTurn() {
        throttle.withLock {
            val now = System.currentTimeMillis()
            val wait = maxOf(pausedUntil - now, lastRequestAt + MIN_GAP_MS - now)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private suspend fun musicSearch(query: String, params: String?): String? {
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", MUSIC_CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US")
        visitorData?.let { client.put("visitorData", it) }
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("query", query)
        if (params != null) body.put("params", params)
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val response = Http.execute(request)
        if (visitorData == null) visitorData = visitorPattern.find(response)?.groupValues?.get(1)
        return bestMusicResult(response)
    }

    private suspend fun webSearch(query: String): String? {
        val url = "https://www.youtube.com/results".toHttpUrl().newBuilder()
            .addQueryParameter("search_query", query)
            .build()
        // SOCS cookie skips the EU cookie-consent interstitial.
        val html = Http.execute(Request.Builder().url(url).header("Cookie", "SOCS=CAI").build())
        return (webVideoPattern.find(html) ?: videoIdPattern.find(html))?.groupValues?.get(1)
    }

    private fun describe(e: Exception): String = when (e) {
        is HttpException -> if (e.code == 429) "YouTube rate limit (HTTP 429)" else "HTTP ${e.code}"
        is java.io.InterruptedIOException -> "timed out"
        is java.net.UnknownHostException -> "no internet connection"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Creates a private playlist on the signed-in account and returns its ID. */
    suspend fun createPlaylist(accessToken: String, title: String): String {
        val body = JSONObject()
            .put("snippet", JSONObject().put("title", title).put("description", "Imported from Spotify via Spot2YTM"))
            .put("status", JSONObject().put("privacyStatus", "private"))
        val created = dataApi("https://www.googleapis.com/youtube/v3/playlists?part=snippet,status", accessToken, body)
        return JSONObject(created).getString("id")
    }

    /** Adds one video to the end of a playlist. */
    suspend fun addToPlaylist(accessToken: String, playlistId: String, videoId: String) {
        val body = JSONObject().put(
            "snippet", JSONObject()
                .put("playlistId", playlistId)
                .put("resourceId", JSONObject().put("kind", "youtube#video").put("videoId", videoId))
        )
        dataApi("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet", accessToken, body)
    }

    /** POSTs to the YouTube Data API, turning HTTP errors into [GoogleApiError] with Google's reason attached. */
    private suspend fun dataApi(url: String, accessToken: String, body: JSONObject): String = try {
        Http.execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(jsonType))
                .build()
        )
    } catch (e: HttpException) {
        throw GoogleApiError.from(e)
    }

    fun playlistMusicUrl(playlistId: String) = "https://music.youtube.com/playlist?list=$playlistId"

    companion object {
        private const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val MUSIC_CLIENT_VERSION = "1.20261013.15.00"
        private const val SONGS_FILTER_GIVE_UP = 3
        private const val MIN_GAP_MS = 250L
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MS = 2_000L
        private const val RATE_LIMIT_PAUSE_MS = 10_000L

        private val featuring = Regex("""\s*[(\[](feat\.?|ft\.?|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
        private val dashSuffix = Regex("""\s+-\s+(from|remaster|remastered|live|single|radio edit|\d{4} remaster).*$""", RegexOption.IGNORE_CASE)
        private val brackets = Regex("""\s*[(\[][^)\]]*[)\]]""")

        private val musicVideoIdPattern = Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""")
        private val musicVideoTypePattern = Regex(""""musicVideoType":"MUSIC_VIDEO_TYPE_(\w+)"""")

        /**
         * Picks the best YouTube Music result: official audio track (ATV) over official music video (OMV)
         * over user uploads, never podcast episodes. Each result's type tag sits just after its videoId.
         */
        fun bestMusicResult(response: String): String? {
            var best: String? = null
            var bestRank = Int.MAX_VALUE
            for (match in musicVideoIdPattern.findAll(response)) {
                val after = response.substring(match.range.last + 1, minOf(response.length, match.range.last + 400))
                val type = musicVideoTypePattern.find(after)?.groupValues?.get(1)
                if (type != null && type.startsWith("PODCAST")) continue
                val rank = when (type) {
                    "ATV" -> 0
                    "OMV" -> 1
                    null -> 3
                    else -> 2
                }
                if (rank < bestRank) {
                    best = match.groupValues[1]
                    bestRank = rank
                    if (rank == 0) break
                }
            }
            return best
        }

        /** "Noah Kahan, X" + "Draw You Out - From The Hunger Games…" → "Noah Kahan Draw You Out". */
        fun simplifiedQuery(track: Track): String {
            val artist = track.artist.split(",", "&", " x ").first().trim()
            val title = track.title.replace(featuring, "").replace(dashSuffix, "").replace(brackets, "").trim()
            return "$artist $title".trim()
        }

        /** watch_videos accepts up to 50 IDs per link. */
        fun queueLinks(videoIds: List<String>): List<String> =
            videoIds.chunked(50).map { "https://www.youtube.com/watch_videos?video_ids=${it.joinToString(",")}" }
    }
}
