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

    @Test fun simplifiesQueriesForRetry() {
        assertEquals(
            "Noah Kahan Draw You Out",
            YouTubeRepository.simplifiedQuery(Track("Draw You Out - From The Hunger Games: Sunrise on the Reaping - The Album", "Noah Kahan, The Hunger Games")),
        )
        assertEquals("Dave Raindance", YouTubeRepository.simplifiedQuery(Track("Raindance (feat. Tems)", "Dave, Tems")))
        assertEquals("Sam Fender Rein Me In", YouTubeRepository.simplifiedQuery(Track("Rein Me In (with Olivia Dean)", "Sam Fender, Olivia Dean")))
        assertEquals("Queen Bohemian Rhapsody", YouTubeRepository.simplifiedQuery(Track("Bohemian Rhapsody - Remastered 2011", "Queen")))
    }

    @Test fun prefersOfficialAudioOverUploads() {
        fun item(id: String, type: String) =
            """{"videoId":"$id","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_$type"}}}"""
        val response = listOf(item("podcast0001", "PODCAST_EPISODE"), item("fanupload01", "UGC"), item("officialvid", "OMV"), item("audiotrack1", "ATV")).joinToString(",")
        assertEquals("audiotrack1", YouTubeRepository.bestMusicResult(response))
        assertEquals("officialvid", YouTubeRepository.bestMusicResult(listOf(item("fanupload01", "UGC"), item("officialvid", "OMV")).joinToString(",")))
        assertEquals("fanupload01", YouTubeRepository.bestMusicResult(listOf(item("podcast0001", "PODCAST_EPISODE"), item("fanupload01", "UGC")).joinToString(",")))
        assertEquals(null, YouTubeRepository.bestMusicResult(item("podcast0001", "PODCAST_EPISODE")))
    }
}
