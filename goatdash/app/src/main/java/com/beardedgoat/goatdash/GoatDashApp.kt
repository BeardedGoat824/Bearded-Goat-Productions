package com.beardedgoat.goatdash

import android.app.Application
import com.beardedgoat.goatdash.dash.DashboardStore
import com.beardedgoat.goatdash.music.MusicController
import com.beardedgoat.goatdash.obd.ObdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the single OBD connection, music controller and dashboard layout shared by the phone UI and Android Auto. */
class GoatDashApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var dashboard: DashboardStore
        private set
    lateinit var obd: ObdManager
        private set
    lateinit var music: MusicController
        private set

    override fun onCreate() {
        super.onCreate()
        dashboard = DashboardStore(this)
        obd = ObdManager(this, dashboard, appScope)
        music = MusicController(this).also { it.start() }
    }
}
