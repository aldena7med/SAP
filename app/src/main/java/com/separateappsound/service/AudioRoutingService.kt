package com.separateappsound.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.separateappsound.R
import com.separateappsound.ui.MainActivity
import com.separateappsound.util.AudioRoutingManager
import com.separateappsound.util.RouteRepository

class AudioRoutingService : Service() {

    private lateinit var routingManager: AudioRoutingManager
    private lateinit var repository: RouteRepository

    companion object {
        const val CHANNEL_ID = "audio_routing_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.separateappsound.STOP_SERVICE"
        const val ACTION_START = "com.separateappsound.START_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        routingManager = AudioRoutingManager(this)
        repository = RouteRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRouting()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                routingManager.startMonitoring()
                applyAllActiveRoutes()
            }
        }
        return START_STICKY
    }

    private fun applyAllActiveRoutes() {
        val activeRoutes = repository.getActiveRoutes()
        for (route in activeRoutes) {
            routingManager.applyRouting(route)
        }
    }

    private fun stopRouting() {
        val activeRoutes = repository.getActiveRoutes()
        for (route in activeRoutes) {
            routingManager.removeRouting(route)
        }
        routingManager.stopMonitoring()
    }

    override fun onDestroy() {
        routingManager.stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val activeCount = repository.getActiveRoutes().size

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMain = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRoutingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (activeCount == 0) "No active routes"
        else "$activeCount app${if (activeCount == 1) "" else "s"} being routed"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Separate App Sound")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pendingMain)
            .addAction(R.drawable.ic_close, "Stop", pendingStop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Routing Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when audio routing is active"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
