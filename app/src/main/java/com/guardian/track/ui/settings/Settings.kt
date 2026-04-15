package com.guardian.track.ui.settings

// [Summary] Structured and concise implementation file.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val fallThreshold: Float = 15.0f,
    val darkMode: Boolean = false,
    val smsSimulationMode: Boolean = true,
    val emergencyNumber: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsManager.fallThreshold,
        prefsManager.darkMode,
        prefsManager.smsSimulationMode,
        prefsManager.emergencyNumber
    ) { threshold, dark, simMode, phone ->
        SettingsUiState(
            fallThreshold = threshold,
            darkMode = dark,
            smsSimulationMode = simMode,
            emergencyNumber = phone
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setFallThreshold(value: Float) {
        viewModelScope.launch { prefsManager.setFallThreshold(value) }
    }

    fun setSmsSimulationMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSmsSimulationMode(enabled) }
    }

    fun setEmergencyNumber(number: String) {
        viewModelScope.launch {
            prefsManager.setEmergencyNumber(number)
            secureStorage.saveEmergencyNumber(number)
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    var thresholdUi by remember { mutableFloatStateOf(state.fallThreshold) }
    var phoneUi by remember { mutableStateOf(state.emergencyNumber) }
    var showContent by remember { mutableStateOf(false) }

    val thresholdProgress by animateFloatAsState(
        targetValue = ((thresholdUi - 5f) / 25f).coerceIn(0f, 1f),
        label = "thresholdProgress"
    )

    LaunchedEffect(state.fallThreshold) {
        thresholdUi = state.fallThreshold
    }
    LaunchedEffect(state.emergencyNumber) {
        if (phoneUi != state.emergencyNumber) phoneUi = state.emergencyNumber
    }
    LaunchedEffect(Unit) {
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF3EA), Color(0xFFFFFBF8), Color(0xFFF3F9FF))
                )
            )
    ) {
        BackgroundOrbs()

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsHeader()

                ProfileCard()

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Emergency Number",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "This number is encrypted and used when alerts are sent.",
                            color = Color(0xFF636B79),
                            fontSize = 13.sp
                        )
                        TextField(
                            value = phoneUi,
                            onValueChange = { input ->
                                phoneUi = input.filter { it.isDigit() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F8FF),
                                unfocusedContainerColor = Color(0xFFF5F8FF),
                                disabledContainerColor = Color(0xFFF5F8FF),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                        Button(
                            onClick = { viewModel.setEmergencyNumber(phoneUi) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E77FF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Number", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Fall Detection Sensitivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Current threshold: ${thresholdUi.toInt()} m/s²",
                            color = Color(0xFF5D6472)
                        )

                        Slider(
                            value = thresholdUi,
                            onValueChange = {
                                val clamped = it.coerceIn(5f, 30f)
                                thresholdUi = clamped
                                viewModel.setFallThreshold(clamped)
                            },
                            valueRange = 5f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2E77FF),
                                activeTrackColor = Color(0xFF2E77FF),
                                inactiveTrackColor = Color(0xFFE4E9F7)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFE7ECFA))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(thresholdProgress)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF2E77FF), Color(0xFF6F67FF))
                                        )
                                    )
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("SMS Simulation Mode", fontWeight = FontWeight.Bold)
                                Text(
                                    "Use this mode for testing safety flows.",
                                    color = Color(0xFF636B79),
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = state.smsSimulationMode,
                                onCheckedChange = { viewModel.setSmsSimulationMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0FAF97),
                                    uncheckedTrackColor = Color(0xFFD5DBE8)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your safety settings are synced and ready.",
                    textAlign = TextAlign.Center,
                    color = Color(0xFF667085),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ProfileCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF8B5E), Color(0xFFE85D8B), Color(0xFF6D7CFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MB",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Profile",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Personal Information",
                        color = Color(0xFF6A7388),
                        fontSize = 12.sp
                    )
                }
            }

            ProfileInfoField(label = "Full Name", value = "Mohamed Bejaoui")
            ProfileInfoField(label = "Email", value = "bejaouimohamed@gmail.com")
            ProfileInfoField(label = "Birth Date", value = "11/04/2003")
            ProfileInfoField(label = "Gender", value = "Male")
        }
    }
}

@Composable
private fun ProfileInfoField(label: String, value: String) {
    TextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF5F8FF),
            unfocusedContainerColor = Color(0xFFF5F8FF),
            disabledContainerColor = Color(0xFFF5F8FF),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color(0xFF344054),
            unfocusedTextColor = Color(0xFF344054)
        )
    )
}

@Composable
private fun BackgroundOrbs() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopStart)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0x33FF7F50), Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomEnd)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0x334E80FF), Color.Transparent)),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun SettingsHeader() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8B5E), Color(0xFFE85D8B), Color(0xFF6D7CFF))
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Preferences",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fine tune detection, alerts, and emergency contact settings.",
                    color = Color.White.copy(alpha = 0.93f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
