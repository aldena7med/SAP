package com.separateappsound.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.separateappsound.R
import com.separateappsound.databinding.ActivityMainBinding
import com.separateappsound.model.AppRoute
import com.separateappsound.service.AudioRoutingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var routeAdapter: RouteAdapter

    companion object {
        const val REQUEST_ADD_ROUTE = 1001
    }

    private val bluetoothPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.loadDevices()
        }
    }

    private val addRouteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadRoutes()
            showSnackbar("Route added successfully")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupObservers()
        setupFab()
        setupServiceSwitch()
        checkPermissions()
        updateServiceStatus()
        checkRootAccess()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRoutes()
        viewModel.loadDevices()
        updateServiceStatus()
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter(
            onToggleActive = { route -> onToggleRoute(route) },
            onDelete = { route -> onDeleteRoute(route) },
            onToggleFavorite = { route -> viewModel.toggleFavorite(route.packageName) },
            onEdit = { route -> openEditRoute(route) }
        )
        binding.recyclerRoutes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = routeAdapter
        }
    }

    private fun setupObservers() {
        viewModel.routes.observe(this) { routes ->
            routeAdapter.submitList(routes)
            updateEmptyState(routes)
            updateServiceToggle(routes)
        }
    }

    private fun setupServiceSwitch() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startRoutingService()
            } else {
                stopRoutingService()
            }
        }
    }

    private fun setupFab() {
        binding.fabAddRoute.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java)
            addRouteLauncher.launch(intent)
        }
    }

    private fun onToggleRoute(route: AppRoute) {
        viewModel.toggleRouteActive(route.packageName)
        val allRoutes = viewModel.routes.value ?: return
        val updatedRoute = allRoutes.firstOrNull { it.packageName == route.packageName }
        if (updatedRoute?.isActive == true) {
            startRoutingService()
        } else {
            // Stop service if no active routes
            if (allRoutes.none { it.isActive && it.packageName != route.packageName }) {
                stopRoutingService()
            }
        }
    }

    private fun onDeleteRoute(route: AppRoute) {
        AlertDialog.Builder(this, R.style.SamsungDialog)
            .setTitle("Remove Route")
            .setMessage("Remove audio route for ${route.appName}?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.deleteRoute(route.packageName)
                showSnackbar("Route removed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditRoute(route: AppRoute) {
        val intent = Intent(this, AppPickerActivity::class.java).apply {
            putExtra(AppPickerActivity.EXTRA_EDIT_ROUTE, route)
        }
        addRouteLauncher.launch(intent)
    }

    private fun updateEmptyState(routes: List<AppRoute>) {
        if (routes.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerRoutes.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerRoutes.visibility = View.VISIBLE
        }
    }

    private fun updateServiceToggle(routes: List<AppRoute>) {
        val hasActive = routes.any { it.isActive }
        binding.switchService.isChecked = hasActive && isServiceRunning()
    }

    private fun updateServiceStatus() {
        val running = isServiceRunning()
        binding.switchService.isChecked = running
        binding.tvServiceStatus.text = if (running) "Sound routing is ON" else "Sound routing is OFF"
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(100).any {
            it.service.className == AudioRoutingService::class.java.name
        }
    }

    private fun startRoutingService() {
        val intent = Intent(this, AudioRoutingService::class.java).apply {
            action = AudioRoutingService.ACTION_START
        }
        startForegroundService(intent)
        updateServiceStatus()
        showSnackbar("Audio routing started")
    }

    private fun stopRoutingService() {
        val intent = Intent(this, AudioRoutingService::class.java).apply {
            action = AudioRoutingService.ACTION_STOP
        }
        startService(intent)
        updateServiceStatus()
    }

    private fun checkPermissions() {
        val permsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (permsNeeded.isNotEmpty()) {
            bluetoothPermLauncher.launch(permsNeeded.toTypedArray())
        }
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.loadDevices()
                viewModel.loadRoutes()
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkRootAccess() {
        val routingManager = com.separateappsound.util.AudioRoutingManager(this)
        Thread {
            val hasRoot = routingManager.hasRootAccess()
            runOnUiThread {
                if (hasRoot) {
                    binding.tvServiceStatus.text = "Sound routing is OFF  ✓ Root detected"
                } else {
                    showSnackbar("Root not detected — routing may be limited")
                }
            }
        }.start()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this, R.style.SamsungDialog)
            .setTitle("How it works")
            .setMessage(
                "Separate App Sound routes audio from a chosen app to a specific device.\n\n" +
                "1. Tap + to add a new route\n" +
                "2. Choose the app (e.g. Spotify)\n" +
                "3. Choose the output device (e.g. Bluetooth speaker)\n" +
                "4. Toggle the route ON\n\n" +
                "💡 For full per-app routing, grant the permission via ADB:\n\n" +
                "adb shell pm grant com.separateappsound android.permission.MODIFY_AUDIO_ROUTING\n\n" +
                "Add the Quick Settings tile for fast toggling from the notification shade."
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
