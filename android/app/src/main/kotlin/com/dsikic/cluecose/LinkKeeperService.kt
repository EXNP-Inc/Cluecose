package com.dsikic.cluecose

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
import androidx.core.content.ContextCompat

/**
 * Link-keeper foreground service — keeps the GATT link warm while the app is
 * backgrounded, mirroring the iOS `bluetooth-central` background mode. The
 * [RealGlucoseRepository] starts it when the encrypted link comes up and
 * stops it when the link tears down (or the sensor is forgotten).
 *
 * The service holds no GATT state itself; the `connectedDevice` foreground
 * type is what protects the Bluetooth connection from doze and the
 * ongoing notification is what the system requires. It is not started by
 * the user and is not exported.
 */
class LinkKeeperService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sensor link",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the direct Bluetooth link to your Libre sensor alive."
            },
        )
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link_keeping)
            .setContentTitle("Cluecose")
            .setContentText("Keeping the sensor link alive")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launch)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "cluecose.link"
        private const val NOTIFICATION_ID = 1

        /** Ask the system to keep the GATT link warm (idempotent; any thread). */
        fun start(context: Context) {
            val intent = Intent(context, LinkKeeperService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // startForegroundService throws when the service was just
                // started and the process is dying; not fatal.
                android.util.Log.w(TAG, "startForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkKeeperService::class.java))
        }

        private const val TAG = "CluecoseLink"
    }
}
