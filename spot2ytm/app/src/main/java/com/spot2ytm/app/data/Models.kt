package com.spot2ytm.app.data

enum class MatchStatus { PENDING, SEARCHING, MATCHED, NOT_FOUND }

data class Track(
    val title: String,
    val artist: String,
    val videoId: String? = null,
    val status: MatchStatus = MatchStatus.PENDING,
) {
    val searchQuery: String get() = "$artist $title".trim()
}

data class Playlist(val name: String?, val tracks: List<Track>)
