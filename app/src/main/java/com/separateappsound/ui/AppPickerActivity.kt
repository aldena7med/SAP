package com.separateappsound.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.separateappsound.databinding.ActivityAppPickerBinding
import com.separateappsound.model.AppInfo
import com.separateappsound.model.AppRoute
import com.separateappsound.util.AppListHelper
import kotlinx.coroutines.launch

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var appAdapter: AppPickerAdapter
    private var allApps: List<AppInfo> = emptyList()
    private var editingRoute: AppRoute? = null

    companion object {
        const val EXTRA_EDIT_ROUTE = "extra_edit_route"
        const val EXTRA_SELECTED_PACKAGE = "extra_selected_package"
        const val EXTRA_SELECTED_APP_NAME = "extra_selected_app_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Choose App"

        editingRoute = intent.getParcelableExtra(EXTRA_EDIT_ROUTE, AppRoute::class.java)

        setupRecyclerView()
        setupSearch()
        loadApps()
    }

    private fun setupRecyclerView() {
        appAdapter = AppPickerAdapter { appInfo ->
            // Go to device picker
            val intent = Intent(this, DevicePickerActivity::class.java).apply {
                putExtra(DevicePickerActivity.EXTRA_PACKAGE_NAME, appInfo.packageName)
                putExtra(DevicePickerActivity.EXTRA_APP_NAME, appInfo.appName)
                editingRoute?.let { putExtra(DevicePickerActivity.EXTRA_EDIT_ROUTE, it) }
            }
            startActivityForResult(intent, 100)
        }

        binding.recyclerApps.apply {
            layoutManager = LinearLayoutManager(this@AppPickerActivity)
            adapter = appAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            allApps = AppListHelper.getInstalledAudioApps(this@AppPickerActivity)
            binding.progressBar.visibility = View.GONE
            appAdapter.submitList(allApps)
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) allApps
        else allApps.filter { it.appName.contains(query, ignoreCase = true) }
        appAdapter.submitList(filtered)
    }

    @Deprecated("Use ActivityResultLauncher instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
