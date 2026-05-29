package com.separateappsound.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.separateappsound.databinding.ItemDeviceBinding
import com.separateappsound.model.AudioDevice

class DevicePickerAdapter(
    private val devices: List<AudioDevice>,
    private val onClick: (AudioDevice) -> Unit
) : RecyclerView.Adapter<DevicePickerAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: AudioDevice) {
            binding.tvDeviceName.text = device.name
            binding.imgDeviceIcon.setImageResource(device.getTypeIcon())

            val statusText = when {
                device.type == AudioDevice.DeviceType.PHONE_SPEAKER -> "Built-in"
                device.isConnected -> "Connected"
                else -> "Paired, not connected"
            }
            binding.tvDeviceStatus.text = statusText

            // Dim if not connected (except phone speaker)
            binding.root.alpha = if (device.isConnected || device.type == AudioDevice.DeviceType.PHONE_SPEAKER) 1.0f else 0.5f

            binding.root.setOnClickListener {
                if (device.isConnected || device.type == AudioDevice.DeviceType.PHONE_SPEAKER) {
                    onClick(device)
                }
            }

            // Show connected badge
            binding.badgeConnected.visibility = if (device.isConnected) android.view.View.VISIBLE
            else android.view.View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size
}
