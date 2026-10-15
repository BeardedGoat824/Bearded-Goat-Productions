package com.beardedgoat.goatdash.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.beardedgoat.goatdash.GoatDashApp

/**
 * Entry point Android Auto binds to.
 *
 * Android Auto only lets navigation apps draw their own graphics on the car screen, so
 * GoatDash registers in that category (see the manifest) to get a canvas for the gauges.
 */
class DashCarAppService : CarAppService() {

    // GoatDash is sideloaded rather than installed from the Play Store, so we accept any
    // Android Auto host. A Play Store build should switch to an allow-list of known hosts.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = DashSession()
}

class DashSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        // Plugging into the car is a good moment to reconnect to the scanner.
        val app = carContext.applicationContext as GoatDashApp
        app.obd.autoConnectIfIdle()
        app.music.start()
        return CarDashboardScreen(carContext)
    }
}
