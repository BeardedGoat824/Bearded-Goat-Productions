package com.beardedgoat.goatdash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The runtime permissions GoatDash needs, which differ by Android version. */
object Permissions {

    val bluetooth: Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Bluetooth plus the "Connected" notification on Android 13+. */
    val all: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) bluetooth + Manifest.permission.POST_NOTIFICATIONS else bluetooth

    fun hasBluetooth(context: Context) = bluetooth.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
