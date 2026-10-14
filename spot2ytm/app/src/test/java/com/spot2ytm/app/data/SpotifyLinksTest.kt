package com.spot2ytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinksTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test fun extractsIdFromShareUrl() =
        assertEquals(id, SpotifyLinks.extractPlaylistId("https://open.spotify.com/playlist/$id?si=abc123"))

    @Test fun extractsIdFromUri() =
        assertEquals(id, SpotifyLinks.extractPlaylistId("spotify:playlist:$id"))

    @Test fun extractsBareId() = assertEquals(id, SpotifyLinks.extractPlaylistId("  $id "))

    @Test fun rejectsNonPlaylistText() {
        assertNull(SpotifyLinks.extractPlaylistId("https://open.spotify.com/track/$id"))
        assertNull(SpotifyLinks.extractPlaylistId("hello"))
    }

    @Test fun pullsLinkOutOfShareText() = assertEquals(
        "https://open.spotify.com/playlist/$id?si=x",
        SpotifyLinks.extractLink("Check out this playlist! https://open.spotify.com/playlist/$id?si=x"),
    )

    @Test fun findsShortLink() = assertEquals(
        "https://spotify.link/AbCdEf",
        SpotifyLinks.findShortLink("Listen: https://spotify.link/AbCdEf"),
    )

    @Test fun parsesPastedLines() {
        val tracks = SpotifyLinks.parseRawLines("Daft Punk - Get Lucky\n\nAC/DC – Back In Black\nJust A Title\nA\tB")
        assertEquals(
            listOf(
                Track("Get Lucky", "Daft Punk"),
                Track("Back In Black", "AC/DC"),
                Track("Just A Title", ""),
                Track("B", "A"),
            ),
            tracks,
        )
    }

    @Test fun queueLinksChunkAtFifty() {
        val ids = (1..120).map { "vid%08d".format(it) }
        val links = YouTubeRepository.queueLinks(ids)
        assertEquals(3, links.size)
        assertEquals(20, links[2].substringAfter("video_ids=").split(",").size)
    }
}
