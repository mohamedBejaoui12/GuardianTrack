package com.guardian.track.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.guardian.track.databinding.FragmentDashboardBinding
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
//  Fragment
// ─────────────────────────────────────────────

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    // viewModels() delegate: creates or retrieves the ViewModel scoped to this Fragment
    private val viewModel: DashboardViewModel by viewModels()

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!  // only valid between onCreateView and onDestroyView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Collect StateFlow with repeatOnLifecycle — pauses when Fragment is in background
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }

        binding.btnManualAlert.setOnClickListener {
            if (hasLocationPermission()) {
                viewModel.triggerManualAlert()
                NotificationHelper.showIncidentNotification(
                    requireContext(), "Manual Alert", "Alert sent to emergency contact."
                )
            } else {
                binding.tvStatus.text = "Location permission required"
            }
        }
    }

    private fun updateUi(state: DashboardUiState) {
        binding.tvIncidentCount.text = "Total Incidents: ${state.incidentCount}"
        binding.tvLastIncident.text = "Last: ${state.lastIncidentType}"
        binding.tvServiceStatus.text = if (state.serviceRunning) "● Monitoring Active" else "○ Service Stopped"
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // prevent memory leaks — binding holds a reference to the View
    }
}
