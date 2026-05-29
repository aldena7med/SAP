package com.separateappsound.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.separateappsound.databinding.ActivityDevicePickerBinding
import com.separateappsound.model.AppRoute
import com.separateappsound.model.AudioDevice
import com.separateappsound.service.AudioRoutingService
import com.separateappsound.util.BluetoothDeviceManager
import com.separateappsound.util.RouteRepository

class DevicePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicePickerBinding
    private lateinit var deviceAdapter: DevicePickerAdapter
    private lateinit var repository: RouteRepository

    private var packageName: String = ""
    private var appName: String = ""
    private var editingRoute: AppRoute? = null

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_EDIT_ROUTE = "extra_edit_route"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevicePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
        editingRoute = intent.getParcelableExtra(EXTRA_EDIT_ROUTE, AppRoute::class.java)
        repository = RouteRepository(this)

        supportActionBar?.title = "Choose Output for $appName"

        binding.tvAppLabel.text = appName
        loadDevices()
    }

    private fun loadDevices() {
        binding.progressBar.visibility = View.VISIBLE
        val deviceManager = BluetoothDeviceManager(this)
        val devices = deviceManager.getAvailableDevices()
        binding.progressBar.visibility = View.GONE

        deviceAdapter = DevicePickerAdapter(devices) { device ->
            saveRoute(device)
        }
        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@DevicePickerActivity)
            adapter = deviceAdapter
        }

        if (devices.size <= 1) {
            binding.tvNoDevices.visibility = View.VISIBLE
        }
    }

    private fun saveRoute(device: AudioDevice) {
        val route = AppRoute(
            packageName = packageName,
            appName = appName,
            deviceAddress = device.address,
            deviceName = device.name,
            isActive = true,
            isFavorite = editingRoute?.isFavorite ?: false
        )
        repository.saveRoute(route)

        // Start the routing service automatically
        val serviceIntent = Intent(this, AudioRoutingService::class.java).apply {
            action = AudioRoutingService.ACTION_START
        }
        startForegroundService(serviceIntent)

        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
