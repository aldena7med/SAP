package com.separateappsound.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.separateappsound.model.AudioDevice

class BluetoothDeviceManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @SuppressLint("MissingPermission")
    fun getAvailableDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        // Always add phone speaker
        devices.add(
            AudioDevice(
                address = AppRoute.PHONE_SPEAKER_ADDRESS,
                name = AppRoute.PHONE_SPEAKER_NAME,
                type = AudioDevice.DeviceType.PHONE_SPEAKER,
                isConnected = true
            )
        )

        // Add connected Bluetooth audio devices
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                val bondedDevices = bluetoothAdapter.bondedDevices ?: emptySet<BluetoothDevice>()
                val audioDeviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

                val connectedBtAddresses = audioDeviceInfos
                    .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
                    .map { it.address }
                    .toSet()

                for (device in bondedDevices) {
                    val isAudioDevice = device.bluetoothClass?.let { btClass ->
                        btClass.hasService(android.bluetooth.BluetoothClass.Service.AUDIO)
                    } ?: false

                    if (isAudioDevice) {
                        val isConnected = connectedBtAddresses.any {
                            it.equals(device.address, ignoreCase = true)
                        }
                        val deviceType = inferDeviceType(device)
                        devices.add(
                            AudioDevice(
                                address = device.address,
                                name = device.name ?: "Unknown Device",
                                type = deviceType,
                                isConnected = isConnected
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Permission not granted yet
            }
        }

        // Add wired headphones if connected
        val wiredDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        if (wiredDevices.isNotEmpty()) {
            devices.add(
                AudioDevice(
                    address = "WIRED_${wiredDevices.first().id}",
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
            name.contains("airpod") || name.contains("headset") -> AudioDevice.DeviceType.BLUETOOTH_HEADPHONES
            name.contains("speaker") || name.contains("soundbar") ||
            name.contains("home") || name.contains("portable") -> AudioDevice.DeviceType.BLUETOOTH_SPEAKER
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
