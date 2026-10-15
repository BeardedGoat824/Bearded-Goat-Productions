package com.spot2ytm.app.data

import android.content.Context
import com.spot2ytm.app.BuildConfig

/** Small persisted settings store backed by SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("spot2ytm", Context.MODE_PRIVATE)

    var spotifyClientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: BuildConfig.SPOTIFY_CLIENT_ID
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var spotifyClientSecret: String
        get() = prefs.getString(KEY_CLIENT_SECRET, null) ?: BuildConfig.SPOTIFY_CLIENT_SECRET
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value).apply()

    var youtubeToken: String
        get() = prefs.getString(KEY_YT_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_YT_TOKEN, value).apply()

    private companion object {
        const val KEY_CLIENT_ID = "spotify_client_id"
        const val KEY_CLIENT_SECRET = "spotify_client_secret"
        const val KEY_YT_TOKEN = "youtube_token"
    }
}
