package com.spot2ytm.app.data

object SpotifyLinks {
    private val uriPattern = Regex("""spotify:playlist:([a-zA-Z0-9]{22})""")
    private val urlPattern = Regex("""playlist/([a-zA-Z0-9]{22})""")
    private val bareIdPattern = Regex("""^[a-zA-Z0-9]{22}$""")
    private val shortLinkPattern = Regex("""https?://(?:spotify\.link|spotify\.app\.link)/\S+""")
    private val anyLinkPattern = Regex("""(https?://\S*spotify\S*|spotify:playlist:[a-zA-Z0-9]{22})""")

    fun extractPlaylistId(raw: String): String? {
        val trimmed = raw.trim()
        uriPattern.find(trimmed)?.let { return it.groupValues[1] }
        urlPattern.findAll(trimmed).lastOrNull()?.let { return it.groupValues[1] }
        if (bareIdPattern.matches(trimmed)) return trimmed
        return null
    }

    fun findShortLink(raw: String): String? = shortLinkPattern.find(raw)?.value

    /** Pulls the Spotify link out of share text like "Check out this playlist! https://open.spotify.com/...". */
    fun extractLink(sharedText: String): String = anyLinkPattern.find(sharedText)?.value ?: sharedText.trim()

    /** Parses lines like "Artist - Title" (also en/em dashes or tabs). Lines without a separator become titles. */
    fun parseRawLines(raw: String): List<Track> {
        val separator = Regex("""\s+[-–—]\s+|\t""")
        return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split(separator)
            if (parts.size >= 2) {
                Track(title = parts.drop(1).joinToString(" - ").trim(), artist = parts[0].trim())
            } else {
                Track(title = line, artist = "")
            }
        }
    }
}
