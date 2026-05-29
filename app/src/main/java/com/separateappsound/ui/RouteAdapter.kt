package com.separateappsound.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.separateappsound.R
import com.separateappsound.databinding.ItemRouteBinding
import com.separateappsound.model.AppRoute

class RouteAdapter(
    private val onToggleActive: (AppRoute) -> Unit,
    private val onDelete: (AppRoute) -> Unit,
    private val onToggleFavorite: (AppRoute) -> Unit,
    private val onEdit: (AppRoute) -> Unit
) : ListAdapter<AppRoute, RouteAdapter.RouteViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppRoute>() {
            override fun areItemsTheSame(old: AppRoute, new: AppRoute) = old.packageName == new.packageName
            override fun areContentsTheSame(old: AppRoute, new: AppRoute) = old == new
        }
    }

    inner class RouteViewHolder(private val binding: ItemRouteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(route: AppRoute) {
            val pm = binding.root.context.packageManager

            // App icon
            try {
                binding.imgAppIcon.setImageDrawable(pm.getApplicationIcon(route.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                binding.imgAppIcon.setImageResource(R.drawable.ic_app_placeholder)
            }

            binding.tvAppName.text = route.appName
            binding.tvDeviceName.text = "→  ${route.deviceName}"

            // Active toggle
            binding.switchActive.isChecked = route.isActive
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.setOnCheckedChangeListener { _, _ ->
                onToggleActive(route)
            }

            // Favorite star
            val starIcon = if (route.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            binding.btnFavorite.setImageResource(starIcon)
            binding.btnFavorite.setOnClickListener { onToggleFavorite(route) }

            // Edit
            binding.btnEdit.setOnClickListener { onEdit(route) }

            // Delete
            binding.btnDelete.setOnClickListener { onDelete(route) }

            // Card styling based on active state
            binding.cardRoute.alpha = if (route.isActive) 1.0f else 0.7f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
