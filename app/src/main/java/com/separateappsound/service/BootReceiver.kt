package com.separateappsound.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.separateappsound.util.RouteRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = RouteRepository(context)
            // Only auto-start if there are active routes
            if (repository.getActiveRoutes().isNotEmpty()) {
                val serviceIntent = Intent(context, AudioRoutingService::class.java).apply {
                    action = AudioRoutingService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
