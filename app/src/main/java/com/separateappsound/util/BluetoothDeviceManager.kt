package com.separateappsound.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.separateappsound.model.AppRoute
import com.separateappsound.model.AudioDevice

class BluetoothDeviceManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @SuppressLint("MissingPermission")
    fun getAvailableDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        devices.add(
            AudioDevice(
                address = AppRoute.PHONE_SPEAKER_ADDRESS,
                name = AppRoute.PHONE_SPEAKER_NAME,
                type = AudioDevice.DeviceType.PHONE_SPEAKER,
                isConnected = true
            )
        )

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                val bondedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices ?: emptySet()

                val connectedA2dp = try {
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.A2DP)
                        ?.map { it.address.lowercase() }?.toSet() ?: emptySet()
                } catch (e: Exception) { emptySet() }

                val connectedHeadset = try {
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.HEADSET)
                        ?.map { it.address.lowercase() }?.toSet() ?: emptySet()
                } catch (e: Exception) { emptySet() }

                val connectedHearingAid = try {
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.HEARING_AID)
                        ?.map { it.address.lowercase() }?.toSet() ?: emptySet()
                } catch (e: Exception) { emptySet() }

                val audioOutputAddresses = audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    }
                    .map { it.address.lowercase() }
                    .toSet()

                val allConnectedAddresses = connectedA2dp + connectedHeadset + connectedHearingAid + audioOutputAddresses

                for (device in bondedDevices) {
                    val address = device.address.lowercase()
                    val isConnected = allConnectedAddresses.contains(address)
                    devices.add(
                        AudioDevice(
                            address = device.address,
                            name = device.name ?: "Unknown Device",
                            type = inferDeviceType(device),
                            isConnected = isConnected
                        )
                    )
                }
            } catch (e: SecurityException) { }
        }

        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            .firstOrNull()
            ?.let {
                devices.add(
                    AudioDevice(
                        address = "WIRED_${it.id}",
                        name = "Wired Headphones",
                        type = AudioDevice.DeviceType.WIRED_HEADPHONES,
                        isConnected = true
                    )
                )
            }

        return devices
    }

    @SuppressLint("MissingPermission")
    private fun inferDeviceType(device: BluetoothDevice): AudioDevice.DeviceType {
        val name = device.name?.lowercase() ?: ""
        return when {
            name.contains("headphone") || name.contains("earphone") ||
            name.contains("earbuds") || name.contains("buds") ||
            name.contains("airpod") || name.contains("headset") ->
                AudioDevice.DeviceType.BLUETOOTH_HEADPHONES
            name.contains("speaker") || name.contains("soundbar") ||
            name.contains("home") || name.contains("portable") ->
                AudioDevice.DeviceType.BLUETOOTH_SPEAKER
            else -> AudioDevice.DeviceType.BLUETOOTH_HEADPHONES
        }
    }

    fun getAudioDeviceInfoForAddress(address: String): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.address.equals(address, ignoreCase = true) }
    }

    companion object {
        fun getPhoneSpeakerDeviceInfo(audioManager: AudioManager): AudioDeviceInfo? {
            return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
    }
}
