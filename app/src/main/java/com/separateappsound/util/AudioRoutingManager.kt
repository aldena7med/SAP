package com.separateappsound.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.separateappsound.model.AppRoute

class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val repository = RouteRepository(context)
    private val deviceManager = BluetoothDeviceManager(context)
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val TAG = "AudioRoutingManager"
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            handlePlaybackChanged(configs)
        }
    }

    fun startMonitoring() {
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        Log.d(TAG, "Started monitoring audio playback")
    }

    fun stopMonitoring() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        Log.d(TAG, "Stopped monitoring audio playback")
    }

    private fun handlePlaybackChanged(configs: List<AudioPlaybackConfiguration>) {
        val activeRoutes = repository.getActiveRoutes()
        if (activeRoutes.isEmpty()) return

        for (config in configs) {
            val uid = getUidFromConfig(config)
            val packageName = getPackageNameForUid(uid)

            val matchingRoute = activeRoutes.firstOrNull { it.packageName == packageName }
            if (matchingRoute != null) {
                applyRouting(matchingRoute)
            }
        }
    }

    /** AudioPlaybackConfiguration.getClientUid() is a hidden API — use reflection as fallback */
    private fun getUidFromConfig(config: AudioPlaybackConfiguration): Int {
        return try {
            val method = config.javaClass.getMethod("getClientUid")
            method.invoke(config) as? Int ?: -1
        } catch (e: Exception) {
            // fallback: try audioSessionId to map to package
            try {
                val method = config.javaClass.getMethod("getClientPid")
                method.invoke(config) as? Int ?: -1
            } catch (e2: Exception) {
                -1
            }
        }
    }

    fun applyRouting(route: AppRoute) {
        val targetDevice = deviceManager.getAudioDeviceInfoForAddress(route.deviceAddress)
            ?: if (route.deviceAddress == AppRoute.PHONE_SPEAKER_ADDRESS) {
                BluetoothDeviceManager.getPhoneSpeakerDeviceInfo(audioManager)
            } else null

        if (targetDevice != null) {
            Log.d(TAG, "Routing ${route.appName} -> ${route.deviceName}")
            trySetCommunicationDevice(targetDevice)
        }
    }

    fun removeRouting(route: AppRoute) {
        Log.d(TAG, "Removing routing for ${route.appName}")
        audioManager.clearCommunicationDevice()
    }

    private fun trySetCommunicationDevice(device: AudioDeviceInfo) {
        try {
            audioManager.setCommunicationDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set communication device", e)
        }
    }

    private fun getPackageNameForUid(uid: Int): String? {
        if (uid < 0) return null
        return try {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun getActivePlayingPackages(): List<String> {
        return audioManager.activePlaybackConfigurations
            .mapNotNull { config -> getPackageNameForUid(getUidFromConfig(config)) }
            .distinct()
    }
}
