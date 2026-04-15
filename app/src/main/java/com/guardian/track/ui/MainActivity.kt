package com.guardian.track.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guardian.track.service.SurveillanceService
import com.guardian.track.ui.dashboard.DashboardScreen
import com.guardian.track.ui.dashboard.DashboardViewModel
import com.guardian.track.ui.history.HistoryScreen
import com.guardian.track.ui.history.HistoryViewModel
import com.guardian.track.ui.settings.SettingsScreen
import com.guardian.track.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            android.util.Log.d("MainActivity", "$permission granted=$granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianTrackComposeApp(
                dashboardViewModel = dashboardViewModel,
                historyViewModel = historyViewModel,
                settingsViewModel = settingsViewModel
            )
        }

        requestPermissions()
        SurveillanceService.startService(this)
    }

    private fun requestPermissions() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.SEND_SMS)
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

private enum class AppScreen(val title: String) {
    DASHBOARD("Home"),
    HISTORY("Incident Log"),
    SETTINGS("Preferences")
}

@Composable
private fun GuardianTrackComposeApp(
    dashboardViewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    AppScreen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedScreen == screen) {
                                                Brush.radialGradient(
                                                    listOf(Color(0xFFFF8B5E), Color(0xFFE85D8B))
                                                )
                                            } else {
                                                Brush.radialGradient(
                                                    listOf(Color(0xFFADB5C4), Color(0xFFADB5C4))
                                                )
                                            }
                                        )
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedScreen == screen) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color(0xFF2A3350),
                                unselectedTextColor = Color(0xFF8791A4),
                                unselectedIconColor = Color(0xFFADB5C4),
                                indicatorColor = Color(0x22FF8B5E)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                when (selectedScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(dashboardViewModel)
                    AppScreen.HISTORY -> HistoryScreen(historyViewModel)
                    AppScreen.SETTINGS -> SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}