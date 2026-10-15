package com.spot2ytm.app.data

import org.json.JSONObject

/**
 * A failed YouTube Data API call, with Google's machine-readable [reason]
 * (e.g. "quotaExceeded") and a plain-language [explanation] of what to do about it.
 */
class GoogleApiError(val code: Int, val reason: String?, val googleMessage: String?) : Exception(
    "HTTP $code${reason?.let { " ($it)" } ?: ""}${googleMessage?.let { ": $it" } ?: ""}"
) {
    /** Errors that will hit every remaining song too, so adding songs should stop and be resumed later. */
    val stopsWholeExport: Boolean
        get() = code == 401 || reason in STOPPING_REASONS

    val explanation: String
        get() = when {
            code == 401 ->
                "Your Google token expired or isn't valid (they only last 1 hour). Get a fresh one from OAuth Playground and paste it in."
            reason in QUOTA_REASONS ->
                "Google's daily limit is used up for the app you signed in through. Every playlist and every song costs 50 of its " +
                    "10,000 daily units (about 190 songs a day), and OAuth Playground's built-in shared app is often already empty. " +
                    "It resets at midnight Pacific time, or use your own Google Cloud credentials in OAuth Playground (gear icon, top right)."
            reason in SCOPE_REASONS ->
                "That token doesn't have permission to manage your YouTube account. In OAuth Playground step 1, pick " +
                    "\"https://www.googleapis.com/auth/youtube\", tap Authorize APIs, then exchange for a new token."
            reason in CHANNEL_REASONS ->
                "Google won't let this account make playlists yet. Usually the account has no YouTube channel: open YouTube, " +
                    "tap your profile picture → Create a channel, then try again."
            reason == "accessNotConfigured" || reason == "SERVICE_DISABLED" ->
                "The YouTube Data API v3 isn't turned on for the Google Cloud project behind your token. Enable it in Google Cloud Console → APIs & Services."
            reason == "maxPlaylistExceeded" ->
                "Your YouTube channel has hit its maximum number of playlists. Delete some old ones and try again."
            else ->
                "Google refused the request (HTTP $code${reason?.let { ", $it" } ?: ""})${googleMessage?.let { ": $it" } ?: "."}"
        }

    companion object {
        private val QUOTA_REASONS = setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded", "RATE_LIMIT_EXCEEDED")
        private val SCOPE_REASONS = setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "forbiddenScope")
        private val CHANNEL_REASONS = setOf("playlistForbidden", "youtubeSignupRequired", "channelNotFound", "channelClosed", "channelSuspended")
        private val STOPPING_REASONS = QUOTA_REASONS + SCOPE_REASONS + CHANNEL_REASONS + setOf("accessNotConfigured", "SERVICE_DISABLED", "playlistNotFound")

        /** Reads Google's standard error body: {"error":{"code":403,"message":"…","errors":[{"reason":"…"}],"details":[{"reason":"…"}]}}. */
        fun from(e: HttpException): GoogleApiError {
            val error = runCatching { JSONObject(e.body).optJSONObject("error") }.getOrNull()
            val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.ifBlank { null }
                ?: error?.optJSONArray("details")?.let { details ->
                    (0 until details.length()).firstNotNullOfOrNull { details.optJSONObject(it)?.optString("reason")?.ifBlank { null } }
                }
            val message = error?.optString("message")?.ifBlank { null }
                ?.replace(Regex("<[^>]+>"), "") // Google sometimes wraps parts of the message in HTML links
            return GoogleApiError(e.code, reason, message)
        }
    }
}
