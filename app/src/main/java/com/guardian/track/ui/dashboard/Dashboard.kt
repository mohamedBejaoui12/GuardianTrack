package com.guardian.track.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State — one immutable data class
// ─────────────────────────────────────────────

/**
 * Represents everything the Dashboard UI needs to display.
 * Immutable data class — the ViewModel only emits new instances, never mutates.
 */
data class DashboardUiState(
    val incidentCount: Int = 0,
    val serviceRunning: Boolean = true,
    val lastIncidentType: String = "None"
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

/**
 * DashboardViewModel — survives configuration changes (screen rotation).
 *
 * Why this is important: when the screen rotates, Android destroys and recreates
 * the Fragment. Without a ViewModel, all UI state would be lost. The ViewModel
 * lives in the ViewModelStore which survives rotation.
 *
 * StateFlow<DashboardUiState> is the single observable output — the Fragment
 * observes this and redraws whenever it changes.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val fusedLocation: FusedLocationProviderClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collect the Room Flow in viewModelScope.
        // viewModelScope is automatically cancelled when the ViewModel is cleared.
        viewModelScope.launch {
            incidentRepository.getAllIncidents().collect { incidents ->
                _uiState.update { current ->
                    current.copy(
                        incidentCount = incidents.size,
                        lastIncidentType = incidents.firstOrNull()?.type ?: "None"
                    )
                }
            }
        }
    }

    /**
     * Called when user taps the manual alert button.
     * Gets current GPS location then saves a MANUAL incident.
     */
    @Suppress("MissingPermission")
    fun triggerManualAlert() {
        viewModelScope.launch {
            try {
                val location = fusedLocation.lastLocation.await()
                incidentRepository.saveAndSync(
                    type = "MANUAL",
                    latitude = location?.latitude ?: 0.0,
                    longitude = location?.longitude ?: 0.0
                )
            } catch (e: Exception) {
                incidentRepository.saveAndSync("MANUAL", 0.0, 0.0)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Modern Compose UI
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("") }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF4E8),
                            Color(0xFFFFFBF7),
                            Color(0xFFF8FAFF)
                        )
                    )
                )
        ) {
            DecorativeBackground()

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(650)) +
                    slideInVertically(initialOffsetY = { it / 5 }, animationSpec = tween(650))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroHeader(state = state)
                    InsightStrip(state = state)
                    AlertSection(
                        statusText = statusText,
                        onAlertClick = {
                            if (hasLocationPermission(context)) {
                                viewModel.triggerManualAlert()
                                NotificationHelper.showIncidentNotification(
                                    context,
                                    "Manual Alert Text",
                                    "Alert sent to emergency contact."
                                )
                                statusText = "Alert sent successfully"
                            } else {
                                statusText = "Location permission is required"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DecorativeBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0x33FF8B5E), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0x335B8CFF), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun HeroHeader(state: DashboardUiState) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFF8B5E), Color(0xFFFF5D8F), Color(0xFF6D7CFF))
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Guardian Track",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (state.incidentCount == 0) {
                        "All clear. You are protected and being monitored in real time."
                    } else {
                        "Stay calm. Monitoring remains active with ${state.incidentCount} logged events."
                    },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                LiveStatusPill(isRunning = state.serviceRunning)
            }
        }
    }
}

@Composable
private fun LiveStatusPill(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusScale"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                color = if (isRunning) Color(0x33B9FF9E) else Color(0x33FF9E9E)
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (isRunning) Color(0xFFB9FF9E) else Color(0xFFFF9E9E)
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(if (isRunning) Color(0xFF7DFF4A) else Color(0xFFFF6666))
            )
            Text(
                text = if (isRunning) "Live monitoring ON" else "Monitoring paused",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InsightStrip(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InsightCard(
            title = "Incidents",
            value = state.incidentCount.toString(),
            subtitle = if (state.incidentCount == 0) "No recent alerts" else "Review incident history",
            color = Color(0xFF2A77FF),
            modifier = Modifier.weight(1f)
        )
        InsightCard(
            title = "Last Type",
            value = state.lastIncidentType,
            subtitle = "Most recent trigger",
            color = when (state.lastIncidentType) {
                "MANUAL" -> Color(0xFFE5486A)
                "AUTO" -> Color(0xFF0FAF97)
                else -> Color(0xFF7A7A7A)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InsightCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF6E6E6E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = Color(0xFF7A7A7A),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun AlertSection(
    statusText: String,
    onAlertClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "alertButtonPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonScale"
    )
    val cardTint by animateColorAsState(
        targetValue = if (statusText.contains("required", ignoreCase = true)) {
            Color(0xFFFFECEB)
        } else {
            Color(0xFFEFFBF1)
        },
        label = "statusTint"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Button(
            onClick = onAlertClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53D5F),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SEND MANUAL ALERT",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Instantly notify your trusted contact",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = statusText.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(350)) +
                slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardTint
                )
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF1E2A1F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = "Tip: Keep GPS on for accurate emergency location sharing.",
            color = Color(0xFF667085),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED