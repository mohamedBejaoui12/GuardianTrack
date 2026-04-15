package com.guardian.track.ui.history

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.track.model.Incident
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    /**
     * stateIn converts a cold Flow into a hot StateFlow.
     * SharingStarted.WhileSubscribed(5000): keeps the upstream Flow active for 5s
     * after the last subscriber disappears (e.g. during rotation) to avoid
     * restarting the Room query on every rotation.
     */
    val incidents = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteIncident(id: Long) {
        viewModelScope.launch { incidentRepository.deleteIncident(id) }
    }

    suspend fun getAllForExport() = incidentRepository.getAllForExport()
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val incidents by viewModel.incidents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFFF4E8), Color(0xFFFFFBF7), Color(0xFFF6FAFF))
                )
            )
    ) {
        DecorativeLayer()

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HistoryTopCard(
                    incidentCount = incidents.size,
                    onExportClick = {
                        scope.launch {
                            val allIncidents = viewModel.getAllForExport()
                            val success = CsvExporter.export(context, allIncidents)
                            showExportToast(context, success)
                        }
                    }
                )

                if (incidents.isEmpty()) {
                    EmptyHistoryCard()
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(incidents, key = { it.id }) { incident ->
                            DismissibleIncidentCard(
                                incident = incident,
                                onDelete = { viewModel.deleteIncident(incident.id) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecorativeLayer() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0x33FF9158), Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0x335F86FF), Color.Transparent)),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun HistoryTopCard(incidentCount: Int, onExportClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2E77FF), Color(0xFF7D63FF), Color(0xFFEA5F8E))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Incident History",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Review and manage recorded safety events.",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$incidentCount events",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF2E77FF)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Export CSV", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "No incidents yet", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = "Your timeline is clear. Events will appear here automatically.",
                color = Color(0xFF6E6E6E),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DismissibleIncidentCard(incident: Incident, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            value == SwipeToDismissBoxValue.EndToStart
        }
    )

    val dismissing by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
        }
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (dismissing) Color(0xFFE5486A) else Color(0xFFEFF3FF),
        label = "historySwipeBackground"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        gesturesEnabled = true,
        content = {
            IncidentCardContent(incident)
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                        contentDescription = "Delete incident",
                        tint = if (dismissing) Color.White else Color(0xFFD32F2F)
                    )
                    Text(
                        "Delete",
                        color = if (dismissing) Color.White else Color(0xFFD32F2F),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

@Composable
private fun IncidentCardContent(incident: Incident) {
    val statusColor = if (incident.isSynced) Color(0xFF0FAF97) else Color(0xFFE79A21)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE9ECF6), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${incident.formattedDate} ${incident.formattedTime}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.type,
                    color = if (incident.type == "MANUAL") Color(0xFFE5486A) else Color(0xFF2E77FF),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (incident.isSynced) "Synced" else "Pending",
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (incident.latitude == 0.0 && incident.longitude == 0.0) {
                    "Location unavailable"
                } else {
                    "%.4f, %.4f".format(incident.latitude, incident.longitude)
                },
                color = Color(0xFF5E6573)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Swipe left to delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF808897)
                )
            }
        }
    }
}

private fun showExportToast(context: Context, success: Boolean) {
    Toast.makeText(
        context,
        if (success) "Exported to Documents/" else "Export failed",
        Toast.LENGTH_SHORT
    ).show()
}
