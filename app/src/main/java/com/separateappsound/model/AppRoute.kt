package com.separateappsound.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppRoute(
    val packageName: String,
    val appName: String,
    val deviceAddress: String,        // Bluetooth MAC or "" for phone speaker
    val deviceName: String,
    val isActive: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    companion object {
        const val PHONE_SPEAKER_ADDRESS = "PHONE_SPEAKER"
        const val PHONE_SPEAKER_NAME = "Phone Speaker"
    }
}
