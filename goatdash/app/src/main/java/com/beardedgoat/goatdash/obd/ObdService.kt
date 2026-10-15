package com.beardedgoat.goatdash.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.beardedgoat.goatdash.GoatDashApp
import com.beardedgoat.goatdash.MainActivity
import com.beardedgoat.goatdash.R

/**
 * Keeps the app alive while connected so the adapter link survives the screen turning off or
 * switching apps. The connection itself lives in [ObdManager]; this just holds the
 * "Connected to …" notification.
 */
class ObdService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            (application as GoatDashApp).obd.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "OBD2 connection", NotificationManager.IMPORTANCE_LOW),
        )
        val state = (application as GoatDashApp).obd.state.value
        val name = (state as? ConnState.Connected)?.target?.displayName ?: "your scanner"

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, ObdService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gauge)
            .setContentTitle("GoatDash connected")
            .setContentText("Reading live data from $name")
            .setContentIntent(open)
            .addAction(0, "Disconnect", disconnect)
            .setOngoing(true)
            .build()

        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            // Android 12+ can refuse when we're in the background; carry on without the notification.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "obd_connection"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_DISCONNECT = "com.beardedgoat.goatdash.DISCONNECT"

        fun start(context: Context) {
            // Android may refuse if we're in the background (e.g. started from the car only);
            // the connection still works while the car screen is showing, so that's fine.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ObdService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ObdService::class.java))
        }
    }
}
