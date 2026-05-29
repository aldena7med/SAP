package com.separateappsound.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.separateappsound.R

@Parcelize
data class AudioDevice(
    val address: String,
    val name: String,
    val type: DeviceType,
    val isConnected: Boolean = false
) : Parcelable {
    enum class DeviceType {
        PHONE_SPEAKER,
        BLUETOOTH_HEADPHONES,
        BLUETOOTH_SPEAKER,
        WIRED_HEADPHONES,
        UNKNOWN
    }

    fun getTypeIcon(): Int {
        return when (type) {
            DeviceType.PHONE_SPEAKER -> com.separateappsound.R.drawable.ic_phone_speaker
            DeviceType.BLUETOOTH_HEADPHONES -> com.separateappsound.R.drawable.ic_headphones
            DeviceType.BLUETOOTH_SPEAKER -> com.separateappsound.R.drawable.ic_speaker
            DeviceType.WIRED_HEADPHONES -> com.separateappsound.R.drawable.ic_headphones
            DeviceType.UNKNOWN -> com.separateappsound.R.drawable.ic_speaker
        }
    }
}
