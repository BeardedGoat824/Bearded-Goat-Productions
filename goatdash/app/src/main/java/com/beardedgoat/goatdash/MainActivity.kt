package com.beardedgoat.goatdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.beardedgoat.goatdash.obd.ConnectionTarget
import com.beardedgoat.goatdash.ui.MainScreen
import com.beardedgoat.goatdash.ui.theme.GoatDashTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GoatDashApp
        if (savedInstanceState == null) {
            val needsBluetooth = app.obd.lastTarget.let { it is ConnectionTarget.Classic || it is ConnectionTarget.Ble }
            if (!needsBluetooth || Permissions.hasBluetooth(this)) app.obd.autoConnectIfIdle()
        }

        setContent {
            GoatDashTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(app)
                }
            }
        }
    }
}
