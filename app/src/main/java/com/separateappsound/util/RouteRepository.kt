package com.separateappsound.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.separateappsound.model.AppRoute

class RouteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRoute(route: AppRoute) {
        val routes = getAllRoutes().toMutableList()
        val existing = routes.indexOfFirst { it.packageName == route.packageName }
        if (existing >= 0) {
            routes[existing] = route
        } else {
            routes.add(route)
        }
        saveAll(routes)
    }

    fun deleteRoute(packageName: String) {
        val routes = getAllRoutes().filter { it.packageName != packageName }
        saveAll(routes)
    }

    fun getRoute(packageName: String): AppRoute? {
        return getAllRoutes().firstOrNull { it.packageName == packageName }
    }

    fun getAllRoutes(): List<AppRoute> {
        val json = prefs.getString("all_routes", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AppRoute>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFavorites(): List<AppRoute> {
        return getAllRoutes().filter { it.isFavorite }
    }

    fun toggleFavorite(packageName: String) {
        val routes = getAllRoutes().toMutableList()
        val idx = routes.indexOfFirst { it.packageName == packageName }
        if (idx >= 0) {
            routes[idx] = routes[idx].copy(isFavorite = !routes[idx].isFavorite)
            saveAll(routes)
        }
    }

    fun setRouteActive(packageName: String, active: Boolean) {
        val routes = getAllRoutes().toMutableList()
        val idx = routes.indexOfFirst { it.packageName == packageName }
        if (idx >= 0) {
            routes[idx] = routes[idx].copy(isActive = active)
            saveAll(routes)
        }
    }

    fun getActiveRoutes(): List<AppRoute> {
        return getAllRoutes().filter { it.isActive }
    }

    private fun saveAll(routes: List<AppRoute>) {
        prefs.edit().putString("all_routes", gson.toJson(routes)).apply()
    }
}
