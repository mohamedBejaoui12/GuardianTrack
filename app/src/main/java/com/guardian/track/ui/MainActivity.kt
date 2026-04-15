package com.guardian.track.ui

// [Summary] Structured and concise implementation file.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import kotlinx.coroutines.launch
import androidx.compose.material3.rememberDrawerState

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
            EmergencyDetectorComposeApp(
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
    DASHBOARD("Overview"),
    HISTORY("Incident Logs"),
    SETTINGS("Preferences")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyDetectorComposeApp(
    dashboardViewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    if (!isLoggedIn) {
        FakeLoginScreen(onLogin = { isLoggedIn = true })
        return
    }

    var selectedScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Text(
                            text = "Emergency Detector",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF23304D)
                        )
                        Text(
                            text = "Navigation",
                            color = Color(0xFF6F7A90),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        AppScreen.entries.forEach { screen ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = screen.title,
                                        fontWeight = if (selectedScreen == screen) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Medium
                                        }
                                    )
                                },
                                selected = selectedScreen == screen,
                                onClick = {
                                    selectedScreen = screen
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0x22FF8B5E),
                                    selectedTextColor = Color(0xFF23304D),
                                    unselectedTextColor = Color(0xFF72809A)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = "Logout",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            selected = false,
                            onClick = {
                                selectedScreen = AppScreen.DASHBOARD
                                isLoggedIn = false
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0x22E5486A),
                                selectedTextColor = Color(0xFF9E2742),
                                unselectedTextColor = Color(0xFF9E2742)
                            )
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = selectedScreen.title,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF23304D)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text(text = "≡", fontSize = 22.sp, color = Color(0xFF23304D))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFFF8FAFF)
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    when (selectedScreen) {
                        AppScreen.DASHBOARD -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenLogs = { selectedScreen = AppScreen.HISTORY }
                        )
                        AppScreen.HISTORY -> HistoryScreen(historyViewModel)
                        AppScreen.SETTINGS -> SettingsScreen(settingsViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeLoginScreen(onLogin: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("bejaouimohamed@gmail.com") }
    var password by rememberSaveable { mutableStateOf("123456") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF3EA), Color(0xFFFFFBF8), Color(0xFFF3F9FF))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Emergency Detector",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF23304D)
                )
                Text(
                    text = "Login to continue",
                    color = Color(0xFF6B768E),
                    fontSize = 14.sp
                )

                TextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEFF4FF),
                        unfocusedContainerColor = Color(0xFFEFF4FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                TextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEFF4FF),
                        unfocusedContainerColor = Color(0xFFEFF4FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFFFF8B5E), Color(0xFFFF5D8F), Color(0xFF6D7CFF))
                                )
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Login", fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "Enter your credentials to access the dashboard.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF7D879B),
                    fontSize = 12.sp
                )
            }
        }
    }
}