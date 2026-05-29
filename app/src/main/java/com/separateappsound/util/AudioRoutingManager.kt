package com.separateappsound.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.separateappsound.model.AppRoute

/**
 * AudioRoutingManager handles detecting which app is playing audio and
 * applying preferred audio output routing.
 *
 * IMPORTANT: Android's AudioPolicy/AudioRouting APIs for per-app routing require
 * MODIFY_AUDIO_ROUTING permission which is a system/signature permission.
 * On stock Android (non-Samsung) this must be done via:
 *   1. A system app signed with platform key, OR
 *   2. Shizuku + ADB-granted MODIFY_AUDIO_ROUTING, OR
 *   3. Root access
 *
 * This implementation uses the public AudioManager APIs (AudioFocusRequest,
 * setPreferredDevice on AudioRecord/AudioTrack) where available, and
 * provides a companion guide for enabling via Shizuku/ADB.
 */
class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val repository = RouteRepository(context)
    private val deviceManager = BluetoothDeviceManager(context)
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val TAG = "AudioRoutingManager"

        // ADB command to grant the required permission:
        // adb shell pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING
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
            val uid = config.audioAttributes?.let {
                // Get the package from UID - best effort
                getPackageForUid(config)
            }
            // Note: AudioPlaybackConfiguration.getClientUid() gives UID
            // Map UID -> package name to find matching route
            val clientUid = config.clientUid
            val packageName = getPackageNameForUid(clientUid)

            val matchingRoute = activeRoutes.firstOrNull { it.packageName == packageName }
            if (matchingRoute != null) {
                applyRouting(matchingRoute)
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
            // AudioTrack.setPreferredDevice is the public API for per-stream routing
            // Full per-app routing requires MODIFY_AUDIO_ROUTING (system permission)
            // With that permission granted via ADB/Shizuku:
            // audioManager.setPreferredDeviceForStrategy(strategy, device)
            // Without it, we use communication device as best effort
            trySetCommunicationDevice(targetDevice)
        }
    }

    fun removeRouting(route: AppRoute) {
        Log.d(TAG, "Removing routing for ${route.appName}")
        audioManager.clearCommunicationDevice()
    }

    private fun trySetCommunicationDevice(device: AudioDeviceInfo) {
        try {
            // Android 12+ API - sets preferred output for communications
            audioManager.setCommunicationDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set communication device", e)
        }
    }

    private fun getPackageForUid(config: AudioPlaybackConfiguration): String? {
        return getPackageNameForUid(config.clientUid)
    }

    private fun getPackageNameForUid(uid: Int): String? {
        return try {
            val packages = context.packageManager.getPackagesForUid(uid)
            packages?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun getActivePlayingPackages(): List<String> {
        return audioManager.activePlaybackConfigurations
            .mapNotNull { config ->
                getPackageNameForUid(config.clientUid)
            }
            .distinct()
    }
}
