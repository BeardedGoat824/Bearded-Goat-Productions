package com.beardedgoat.goatdash

import com.beardedgoat.goatdash.dash.DashPage
import com.beardedgoat.goatdash.dash.DashboardConfig
import com.beardedgoat.goatdash.dash.Gauge
import com.beardedgoat.goatdash.dash.MusicButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSettingsTest {

    @Test
    fun musicAndCarSettingsAreSaved() {
        val cfg = DashboardConfig(
            pages = listOf(DashPage("P", 2, 1, listOf(Gauge("RPM"), Gauge(Gauge.MUSIC)))),
            carFullScreen = false,
            musicButtons = listOf(MusicButton.BACK_15, MusicButton.PLAY_PAUSE, MusicButton.FORWARD_30),
            carMusicButtons = true,
        )
        val restored = DashboardConfig.fromJson(cfg.toJson())
        assertEquals(cfg, restored)
        assertTrue(restored.pages[0].gauges[1].isMusic)
    }

    @Test
    fun olderLayoutsGetSensibleDefaults() {
        val old = """{"imperial":false,"pages":[{"name":"P","columns":1,"rows":1,"gauges":[{"pid":"RPM","style":"DIAL","color":-30177}]}]}"""
        val cfg = DashboardConfig.fromJson(old)
        assertTrue(cfg.carFullScreen)
        assertFalse(cfg.carMusicButtons)
        assertEquals(DashboardConfig.DEFAULT_MUSIC_BUTTONS, cfg.musicButtons)
        assertFalse(cfg.imperial)
    }

    @Test
    fun buttonsLoadInOrderWithoutJunk() {
        val json = """{"musicButtons":["NEXT","BOGUS","PREVIOUS","NEXT"],"pages":[{"name":"P","columns":1,"rows":1,"gauges":[{"pid":"MUSIC","style":"DIGITAL","color":0}]}]}"""
        assertEquals(listOf(MusicButton.PREVIOUS, MusicButton.NEXT), DashboardConfig.fromJson(json).musicButtons)
    }

    @Test
    fun carColumnFitsFourButtons() {
        val all = DashboardConfig(DashboardConfig.default().pages, musicButtons = MusicButton.entries.toList())
        assertEquals(
            listOf(MusicButton.PREVIOUS, MusicButton.PLAY_PAUSE, MusicButton.FORWARD_30, MusicButton.NEXT),
            all.carMusicStrip,
        )
        val three = all.copy(musicButtons = DashboardConfig.DEFAULT_MUSIC_BUTTONS)
        assertEquals(DashboardConfig.DEFAULT_MUSIC_BUTTONS, three.carMusicStrip)
    }

    @Test
    fun musicTileIsNotASensor() {
        assertFalse(Gauge.supportsShiftLight(Gauge.MUSIC))
        assertTrue(DashboardConfig.default().pages.any { p -> p.gauges.any { it.isMusic } })
    }
}
