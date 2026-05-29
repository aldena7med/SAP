package com.separateappsound.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.separateappsound.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppListHelper {

    // Apps that are known to use audio and are commonly used with audio routing
    private val AUDIO_CATEGORY_PACKAGES = setOf(
        "com.spotify.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.amazon.music",
        "com.apple.android.music",
        "com.soundcloud.android",
        "deezer.android.app",
        "com.tidal.wave",
        "com.pandora.android",
        "com.iheart.android",
        "com.tunein.player",
        "com.audible.application",
        "fm.castbox.audiobook.radio.podcast",
        "com.google.android.apps.podcasts",
        "com.overcast.podcasts",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.twitch.android.app",
        "com.discord",
        "org.videolan.vlc",
        "com.mx.player",
        "com.google.android.play.games"
    )

    suspend fun getInstalledAudioApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launchableApps = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }

        launchableApps
            .mapNotNull { resolveInfo ->
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == context.packageName) return@mapNotNull null // exclude self

                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    AppInfo(
                        packageName = packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(packageName)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedWith(
                // Sort: known audio apps first, then alphabetically
                compareByDescending<AppInfo> { AUDIO_CATEGORY_PACKAGES.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
    }

    fun isLikelyAudioApp(packageName: String): Boolean {
        return AUDIO_CATEGORY_PACKAGES.contains(packageName)
    }
}
