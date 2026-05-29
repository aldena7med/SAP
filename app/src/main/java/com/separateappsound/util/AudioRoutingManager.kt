package com.separateappsound.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.separateappsound.model.AppRoute
import java.io.BufferedReader
import java.io.InputStreamReader

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
            val packageName = getPackageNameForUid(uid) ?: continue
            val matchingRoute = activeRoutes.firstOrNull { it.packageName == packageName }
            if (matchingRoute != null) {
                applyRouting(matchingRoute)
            }
        }
    }

    fun applyRouting(route: AppRoute) {
        Log.d(TAG, "Applying routing: ${route.appName} -> ${route.deviceName}")

        // Try root-based routing first (works on KernelSU/Magisk without MODIFY_AUDIO_ROUTING)
        val rootSuccess = tryRootRouting(route)

        if (!rootSuccess) {
            // Fallback to communication device API
            val targetDevice = deviceManager.getAudioDeviceInfoForAddress(route.deviceAddress)
                ?: if (route.deviceAddress == AppRoute.PHONE_SPEAKER_ADDRESS)
                    BluetoothDeviceManager.getPhoneSpeakerDeviceInfo(audioManager)
                else null
            targetDevice?.let {
                try { audioManager.setCommunicationDevice(it) } catch (e: Exception) {
                    Log.e(TAG, "setCommunicationDevice failed", e)
                }
            }
        }
    }

    private fun tryRootRouting(route: AppRoute): Boolean {
        return try {
            val uid = getUidForPackage(route.packageName) ?: return false

            if (route.deviceAddress == AppRoute.PHONE_SPEAKER_ADDRESS) {
                // Route this app back to speaker - clear any override
                runAsRoot(listOf(
                    "cmd appops set --uid $uid PLAY_AUDIO allow",
                    "cmd audio set-preferred-device-for-strategy 0 0"  // strategy 0 = media
                ))
            } else {
                // Get the audio device ID for the bluetooth address
                val btDeviceId = getAudioDeviceIdForAddress(route.deviceAddress)

                if (btDeviceId != null) {
                    // Use cmd audio to set preferred device for this app's UID
                    runAsRoot(listOf(
                        // Grant the routing permission to our app via root
                        "pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING",
                        // Set preferred device for media strategy
                        "cmd audio set-preferred-device-for-strategy 0 $btDeviceId"
                    ))
                } else {
                    // Fallback: force bluetooth A2DP output
                    runAsRoot(listOf(
                        "pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING",
                        "cmd bluetooth_manager enable"
                    ))
                    false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Root routing failed", e)
            false
        }
    }

    private fun runAsRoot(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream.bufferedWriter()
            for (cmd in commands) {
                os.write(cmd)
                os.newLine()
            }
            os.write("exit")
            os.newLine()
            os.flush()
            os.close()
            val exitCode = process.waitFor()
            val errors = BufferedReader(InputStreamReader(process.errorStream)).readText()
            if (errors.isNotBlank()) Log.w(TAG, "Root cmd stderr: $errors")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "runAsRoot failed", e)
            false
        }
    }

    private fun getAudioDeviceIdForAddress(address: String): Int? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?.id
    }

    fun removeRouting(route: AppRoute) {
        Log.d(TAG, "Removing routing for ${route.appName}")
        try {
            runAsRoot(listOf("cmd audio set-preferred-device-for-strategy 0 0"))
            audioManager.clearCommunicationDevice()
        } catch (e: Exception) {
            Log.e(TAG, "removeRouting failed", e)
        }
    }

    private fun getUidForPackage(packageName: String): Int? {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: Exception) { null }
    }

    private fun getUidFromConfig(config: AudioPlaybackConfiguration): Int {
        return try {
            config.javaClass.getMethod("getClientUid").invoke(config) as? Int ?: -1
        } catch (e: Exception) {
            try {
                config.javaClass.getMethod("getClientPid").invoke(config) as? Int ?: -1
            } catch (e2: Exception) { -1 }
        }
    }

    private fun getPackageNameForUid(uid: Int): String? {
        if (uid < 0) return null
        return try {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (e: Exception) { null }
    }

    fun getActivePlayingPackages(): List<String> {
        return audioManager.activePlaybackConfigurations
            .mapNotNull { getPackageNameForUid(getUidFromConfig(it)) }
            .distinct()
    }

    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val result = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            result.contains("uid=0")
        } catch (e: Exception) { false }
    }
}
