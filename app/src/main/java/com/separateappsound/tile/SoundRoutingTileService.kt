package com.separateappsound.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.separateappsound.service.AudioRoutingService
import com.separateappsound.ui.MainActivity
import com.separateappsound.util.RouteRepository

class SoundRoutingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val repository = RouteRepository(this)
        val activeRoutes = repository.getActiveRoutes()

        if (activeRoutes.isNotEmpty()) {
            // Toggle off - stop service
            val intent = Intent(this, AudioRoutingService::class.java).apply {
                action = AudioRoutingService.ACTION_STOP
            }
            startService(intent)
            // Mark all routes inactive
            for (route in activeRoutes) {
                repository.setRouteActive(route.packageName, false)
            }
        } else {
            // Open app to set up routing
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(mainIntent)
        }
        updateTile()
    }

    private fun updateTile() {
        val repository = RouteRepository(this)
        val activeRoutes = repository.getActiveRoutes()
        val tile = qsTile ?: return

        if (activeRoutes.isNotEmpty()) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "${activeRoutes.size} route${if (activeRoutes.size == 1) "" else "s"} active"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "No active routes"
        }
        tile.updateTile()
    }
}
