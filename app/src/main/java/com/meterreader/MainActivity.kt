package com.meterreader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.meterreader.databinding.ActivityMainBinding
import com.meterreader.service.ServiceManager
import com.meterreader.ui.HistoryFragment
import com.meterreader.ui.LiveViewFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) {
                ServiceManager.startService(this)
                maybeRequestIgnoreBatteryOptimizations()
            } else {
                Toast.makeText(
                    this,
                    "Camera and notification permissions are required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupTabs()
        checkPermissionsAndStartService()
    }

    private fun setupTabs() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> LiveViewFragment()
                else -> HistoryFragment()
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_live_view)
                else -> getString(R.string.tab_history)
            }
        }.attach()
    }

    private fun checkPermissionsAndStartService() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            ServiceManager.startService(this)
            maybeRequestIgnoreBatteryOptimizations()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Unattended capture for days requires exemption from Doze/App Standby. Once
     * permissions are granted, ask the user to whitelist the app — but only once,
     * so we don't nag on every launch.
     */
    private fun maybeRequestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        val prefs = getSharedPreferences("meter_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_OPT_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_BATTERY_OPT_REQUESTED, true).apply()

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            // Some OEMs don't expose this screen — safe to ignore.
        }
    }

    companion object {
        private const val KEY_BATTERY_OPT_REQUESTED = "battery_opt_requested"
    }
}
