package com.separateappsound.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.separateappsound.model.AppRoute
import com.separateappsound.model.AudioDevice
import com.separateappsound.util.BluetoothDeviceManager
import com.separateappsound.util.RouteRepository
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RouteRepository(app)
    private val deviceManager = BluetoothDeviceManager(app)

    private val _routes = MutableLiveData<List<AppRoute>>()
    val routes: LiveData<List<AppRoute>> = _routes

    private val _devices = MutableLiveData<List<AudioDevice>>()
    val devices: LiveData<List<AudioDevice>> = _devices

    private val _serviceRunning = MutableLiveData<Boolean>(false)
    val serviceRunning: LiveData<Boolean> = _serviceRunning

    init {
        loadRoutes()
        loadDevices()
    }

    fun loadRoutes() {
        viewModelScope.launch {
            _routes.value = repository.getAllRoutes()
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            _devices.value = deviceManager.getAvailableDevices()
        }
    }

    fun deleteRoute(packageName: String) {
        repository.deleteRoute(packageName)
        loadRoutes()
    }

    fun toggleRouteActive(packageName: String) {
        val route = repository.getRoute(packageName) ?: return
        repository.setRouteActive(packageName, !route.isActive)
        loadRoutes()
    }

    fun toggleFavorite(packageName: String) {
        repository.toggleFavorite(packageName)
        loadRoutes()
    }

    fun getFavorites(): List<AppRoute> = repository.getFavorites()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}
