package com.spot2ytm.app.data

import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class YouTubeRepository {
    private val jsonType = "application/json".toMediaType()
    private val videoIdPattern = Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""")
    private val webVideoPattern = Regex(""""videoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"""")

    /**
     * Finds a video ID for [query] without any API key or quota:
     * 1. YouTube Music search filtered to "Songs" (best audio-only matches)
     * 2. Unfiltered YouTube Music search
     * 3. Regular YouTube search results page
     */
    suspend fun searchVideoId(query: String): String? {
        runCatching { musicSearch(query, SONGS_FILTER) }.getOrNull()?.let { return it }
        runCatching { musicSearch(query, null) }.getOrNull()?.let { return it }
        return runCatching { webSearch(query) }.getOrNull()
    }

    private suspend fun musicSearch(query: String, params: String?): String? {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", MUSIC_CLIENT_VERSION)
                .put("hl", "en")
                .put("gl", "US")))
            .put("query", query)
        if (params != null) body.put("params", params)
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return videoIdPattern.find(Http.execute(request))?.groupValues?.get(1)
    }

    private suspend fun webSearch(query: String): String? {
        val url = "https://www.youtube.com/results".toHttpUrl().newBuilder()
            .addQueryParameter("search_query", query)
            .build()
        // SOCS cookie skips the EU cookie-consent interstitial.
        val html = Http.execute(Request.Builder().url(url).header("Cookie", "SOCS=CAI").build())
        return (webVideoPattern.find(html) ?: videoIdPattern.find(html))?.groupValues?.get(1)
    }

    /** Creates a private playlist on the signed-in account and inserts every video. */
    suspend fun createPlaylist(
        accessToken: String,
        title: String,
        videoIds: List<String>,
        onProgress: (inserted: Int, total: Int) -> Unit,
        onSkip: (videoId: String, reason: String) -> Unit,
    ): CreatedPlaylist {
        val auth = "Bearer $accessToken"
        val createBody = JSONObject()
            .put("snippet", JSONObject().put("title", title).put("description", "Imported from Spotify via Spot2YTM"))
            .put("status", JSONObject().put("privacyStatus", "private"))
        val created = JSONObject(
            Http.execute(
                Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/playlists?part=snippet,status")
                    .header("Authorization", auth)
                    .post(createBody.toString().toRequestBody(jsonType))
                    .build()
            )
        )
        val playlistId = created.getString("id")

        var added = 0
        videoIds.forEachIndexed { index, videoId ->
            onProgress(index, videoIds.size)
            val itemBody = JSONObject().put(
                "snippet", JSONObject()
                    .put("playlistId", playlistId)
                    .put("resourceId", JSONObject().put("kind", "youtube#video").put("videoId", videoId))
            )
            try {
                Http.execute(
                    Request.Builder()
                        .url("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet")
                        .header("Authorization", auth)
                        .post(itemBody.toString().toRequestBody(jsonType))
                        .build()
                )
                added++
            } catch (e: HttpException) {
                onSkip(videoId, "HTTP ${e.code}")
            }
            delay(300) // stay well under YouTube's write rate limits
        }
        onProgress(videoIds.size, videoIds.size)
        return CreatedPlaylist(playlistId, added)
    }

    data class CreatedPlaylist(val id: String, val added: Int) {
        val musicUrl: String get() = "https://music.youtube.com/playlist?list=$id"
    }

    companion object {
        private const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val MUSIC_CLIENT_VERSION = "1.20261013.15.00"

        /** watch_videos accepts up to 50 IDs per link. */
        fun queueLinks(videoIds: List<String>): List<String> =
            videoIds.chunked(50).map { "https://www.youtube.com/watch_videos?video_ids=${it.joinToString(",")}" }
    }
}
