package com.separateappsound.util

import android.content.Context
import android.media.AudioFormat
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
    private var activeAudioPolicy: Any? = null

    companion object {
        const val TAG = "AudioRoutingManager"
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            handlePlaybackChanged(configs)
        }
    }

    fun startMonitoring() {
        grantRoutingPermissionViaRoot()
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        Log.d(TAG, "Started monitoring")
    }

    fun stopMonitoring() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        releaseAudioPolicy()
    }

    private fun grantRoutingPermissionViaRoot() {
        runAsRoot(listOf(
            "appops set com.separateappsound MODIFY_AUDIO_ROUTING allow",
            "pm grant --user 0 com.separateappsound android.permission.MODIFY_AUDIO_ROUTING",
            "pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING",
            "cmd appops set com.separateappsound MODIFY_AUDIO_ROUTING 0"
        ))
    }

    private fun handlePlaybackChanged(configs: List<AudioPlaybackConfiguration>) {
        val activeRoutes = repository.getActiveRoutes()
        if (activeRoutes.isEmpty()) return
        for (config in configs) {
            val uid = getUidFromConfig(config)
            val packageName = getPackageNameForUid(uid) ?: continue
            val matchingRoute = activeRoutes.firstOrNull { it.packageName == packageName }
            if (matchingRoute != null) applyRouting(matchingRoute)
        }
    }

    fun applyRouting(route: AppRoute) {
        Log.d(TAG, "Applying routing: ${route.appName} -> ${route.deviceName}")
        val policySuccess = tryAudioPolicyViaReflection(route)
        if (policySuccess) return
        tryRootUidRouting(route)
    }

    private fun tryAudioPolicyViaReflection(route: AppRoute): Boolean {
        return try {
            releaseAudioPolicy()
            val uid = getUidForPackage(route.packageName) ?: return false
            val targetDevice = deviceManager.getAudioDeviceInfoForAddress(route.deviceAddress)
                ?: if (route.deviceAddress == AppRoute.PHONE_SPEAKER_ADDRESS)
                    BluetoothDeviceManager.getPhoneSpeakerDeviceInfo(audioManager)
                else return false

            val audioMixingRuleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
            val audioMixingRuleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
            val audioMixClass = Class.forName("android.media.audiopolicy.AudioMix")
            val audioMixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
            val audioPolicyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
            val audioPolicyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")

            val ruleMatchUid = audioMixingRuleClass.getField("RULE_MATCH_UID").getInt(null)
            val routeFlagRender = audioMixClass.getField("ROUTE_FLAG_RENDER").getInt(null)

            val mixingRuleBuilder = audioMixingRuleBuilderClass.newInstance()
            audioMixingRuleBuilderClass
                .getMethod("addMixRule", Int::class.java, Any::class.java)
                .invoke(mixingRuleBuilder, ruleMatchUid, uid)
            val mixingRule = audioMixingRuleBuilderClass.getMethod("build").invoke(mixingRuleBuilder)

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(48000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            val audioMixBuilder = audioMixBuilderClass
                .getConstructor(audioMixingRuleClass)
                .newInstance(mixingRule)
            audioMixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(audioMixBuilder, audioFormat)
            audioMixBuilderClass.getMethod("setRouteFlags", Int::class.java).invoke(audioMixBuilder, routeFlagRender)
            val audioMix = audioMixBuilderClass.getMethod("build").invoke(audioMixBuilder)

            val policyBuilder = audioPolicyBuilderClass.getConstructor(Context::class.java).newInstance(context)
            audioPolicyBuilderClass.getMethod("addMix", audioMixClass).invoke(policyBuilder, audioMix)
            val policy = audioPolicyBuilderClass.getMethod("build").invoke(policyBuilder)

            val result = AudioManager::class.java
                .getMethod("registerAudioPolicy", audioPolicyClass)
                .invoke(audioManager, policy) as Int

            if (result == 0) {
                try {
                    audioPolicyClass
                        .getMethod("setPreferredDeviceForRule", audioMixingRuleClass, android.media.AudioDeviceInfo::class.java)
                        .invoke(policy, mixingRule, targetDevice)
                } catch (e: Exception) {
                    Log.w(TAG, "setPreferredDeviceForRule unavailable: ${e.message}")
                }
                activeAudioPolicy = policy
                true
            } else {
                Log.w(TAG, "registerAudioPolicy returned: $result")
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "MODIFY_AUDIO_ROUTING not granted yet: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun tryRootUidRouting(route: AppRoute): Boolean {
        return try {
            val uid = getUidForPackage(route.packageName) ?: return false
            val deviceId = if (route.deviceAddress == AppRoute.PHONE_SPEAKER_ADDRESS) {
                BluetoothDeviceManager.getPhoneSpeakerDeviceInfo(audioManager)?.id ?: return false
            } else {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.address.equals(route.deviceAddress, ignoreCase = true) }
                    ?.id ?: return false
            }
            runAsRoot(listOf("cmd audio set-preferred-device-for-uid $uid $deviceId"))
        } catch (e: Exception) {
            Log.e(TAG, "Root UID routing failed", e)
            false
        }
    }

    private fun releaseAudioPolicy() {
        activeAudioPolicy?.let { policy ->
            try {
                val audioPolicyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                AudioManager::class.java
                    .getMethod("unregisterAudioPolicy", audioPolicyClass)
                    .invoke(audioManager, policy)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterAudioPolicy failed: ${e.message}")
            }
            activeAudioPolicy = null
        }
    }

    fun removeRouting(route: AppRoute) {
        releaseAudioPolicy()
        try {
            val uid = getUidForPackage(route.packageName)
            if (uid != null) runAsRoot(listOf("cmd audio remove-preferred-device-for-uid $uid"))
            audioManager.clearCommunicationDevice()
        } catch (e: Exception) {
            Log.e(TAG, "removeRouting failed", e)
        }
    }

    private fun runAsRoot(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream.bufferedWriter()
            for (cmd in commands) { os.write(cmd); os.newLine() }
            os.write("exit"); os.newLine(); os.flush(); os.close()
            val exitCode = process.waitFor()
            val errors = BufferedReader(InputStreamReader(process.errorStream)).readText()
            if (errors.isNotBlank()) Log.w(TAG, "Root stderr: $errors")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "runAsRoot failed", e)
            false
        }
    }

    private fun getUidForPackage(packageName: String): Int? {
        return try { context.packageManager.getApplicationInfo(packageName, 0).uid }
        catch (e: Exception) { null }
    }

    private fun getUidFromConfig(config: AudioPlaybackConfiguration): Int {
        return try { config.javaClass.getMethod("getClientUid").invoke(config) as? Int ?: -1 }
        catch (e: Exception) {
            try { config.javaClass.getMethod("getClientPid").invoke(config) as? Int ?: -1 }
            catch (e2: Exception) { -1 }
        }
    }

    private fun getPackageNameForUid(uid: Int): String? {
        if (uid < 0) return null
        return try { context.packageManager.getPackagesForUid(uid)?.firstOrNull() }
        catch (e: Exception) { null }
    }

    fun getActivePlayingPackages(): List<String> {
        return audioManager.activePlaybackConfigurations
            .mapNotNull { getPackageNameForUid(getUidFromConfig(it)) }.distinct()
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
