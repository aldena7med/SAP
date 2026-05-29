package com.separateappsound.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.separateappsound.databinding.ItemAppPickerBinding
import com.separateappsound.model.AppInfo
import com.separateappsound.util.AppListHelper

class AppPickerAdapter(
    private val onClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppPickerAdapter.AppViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName && a.appName == b.appName
        }
    }

    inner class AppViewHolder(private val binding: ItemAppPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.tvAppName.text = app.appName
            binding.tvPackageName.text = app.packageName
            binding.imgIcon.setImageDrawable(app.icon)

            // Badge for known audio apps
            binding.badgeAudio.visibility = if (AppListHelper.isLikelyAudioApp(app.packageName))
                android.view.View.VISIBLE else android.view.View.GONE

            binding.root.setOnClickListener { onClick(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
