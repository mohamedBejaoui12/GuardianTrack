package com.guardian.track.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.guardian.track.R
import com.guardian.track.databinding.ActivityMainBinding
import com.guardian.track.service.SurveillanceService
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — the single activity in our Single Activity Architecture.
 *
 * Its only job:
 *  1. Set up the NavController (Navigation Component manages Fragment swaps).
 *  2. Set up BottomNavigationView linked to the NavController.
 *  3. Request runtime permissions.
 *  4. Start the SurveillanceService.
 *
 * All business logic lives in ViewModels, not here.
 *
 * @AndroidEntryPoint tells Hilt this is an injection target.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Modern permission request API — replaces onRequestPermissionsResult
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log which permissions were granted/denied
        results.forEach { (permission, granted) ->
            android.util.Log.d("MainActivity", "$permission granted=$granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestPermissions()
        SurveillanceService.startService(this)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // BottomNavigationView automatically handles back stack and tab switching
        binding.bottomNavigation.setupWithNavController(navController)
    }

    /**
     * Requests all permissions the app needs.
     * We request them all at once here for simplicity.
     * In production, request permissions contextually (just before you need them).
     */
    private fun requestPermissions() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            // POST_NOTIFICATIONS only exists on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
