package com.spot2ytm.app.data

import android.util.Base64
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SpotifyRepository {
    private var token: String? = null
    private var tokenExpiryMs = 0L
    private var tokenCredentials: Pair<String, String>? = null

    suspend fun accessToken(clientId: String, clientSecret: String): String {
        val creds = clientId to clientSecret
        val cached = token
        if (cached != null && creds == tokenCredentials && System.currentTimeMillis() < tokenExpiryMs) return cached

        val basic = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $basic")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()
        val json = JSONObject(Http.execute(request))
        val newToken = json.getString("access_token")
        token = newToken
        tokenCredentials = creds
        tokenExpiryMs = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
        return newToken
    }

    /** Official Web API: full playlist with pagination (capped at [maxTracks]). */
    suspend fun fetchViaApi(playlistId: String, clientId: String, clientSecret: String, maxTracks: Int = 1000): Playlist {
        val bearer = "Bearer ${accessToken(clientId, clientSecret)}"
        val meta = JSONObject(
            Http.execute(
                Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/$playlistId?market=US")
                    .header("Authorization", bearer)
                    .build()
            )
        )
        val tracks = mutableListOf<Track>()
        // Spotify has renamed "tracks"/"track" to "items"/"item" on some responses; accept both.
        var page: JSONObject? = meta.optJSONObject("tracks") ?: meta.optJSONObject("items")
        while (page != null) {
            tracks += parseApiItems(page.optJSONArray("items"))
            val next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
            if (next == null || tracks.size >= maxTracks) break
            page = JSONObject(Http.execute(Request.Builder().url(next).header("Authorization", bearer).build()))
        }
        return Playlist(meta.optString("name").ifBlank { null }, tracks)
    }

    private fun parseApiItems(items: JSONArray?): List<Track> {
        if (items == null) return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val t = item.optJSONObject("track") ?: item.optJSONObject("item") ?: return@mapNotNull null
            val name = t.optString("name").ifBlank { return@mapNotNull null }
            val artists = t.optJSONArray("artists")
            val artistNames = (0 until (artists?.length() ?: 0))
                .mapNotNull { artists?.optJSONObject(it)?.optString("name")?.ifBlank { null } }
            Track(title = name, artist = artistNames.joinToString(", "))
        }
    }

    /** Fallback: scrape the public embed page, which needs no credentials (first ~100 tracks). */
    suspend fun fetchViaEmbed(playlistId: String): Playlist {
        val html = Http.execute(Request.Builder().url("https://open.spotify.com/embed/playlist/$playlistId").build())
        val match = Regex("""<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)</script>""").find(html)
            ?: error("Embed page format not recognised")
        val entity = JSONObject(match.groupValues[1])
            .optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
            ?.optJSONObject("data")?.optJSONObject("entity")
            ?: error("Embed page had no playlist data")
        val list = entity.optJSONArray("trackList") ?: JSONArray()
        val tracks = (0 until list.length()).mapNotNull { i ->
            val t = list.optJSONObject(i) ?: return@mapNotNull null
            Track(
                title = t.optString("title").ifBlank { "Unknown Title" },
                artist = t.optString("subtitle").ifBlank { "Unknown Artist" },
            )
        }
        val name = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { null }
        return Playlist(name, tracks)
    }
}
