package com.guardian.track.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.guardian.track.databinding.FragmentSettingsBinding
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.util.SecureStorage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

data class SettingsUiState(
    val fallThreshold: Float = 15.0f,
    val darkMode: Boolean = false,
    val smsSimulationMode: Boolean = true,  // DEFAULT ON — spec requirement
    val emergencyNumber: String = ""
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    /**
     * Combines multiple DataStore Flows into one UI state using combine().
     * Any time one of the four preferences changes, a new SettingsUiState is emitted.
     */
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

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setDarkMode(enabled) }
    }

    fun setSmsSimulationMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSmsSimulationMode(enabled) }
    }

    fun setEmergencyNumber(number: String) {
        viewModelScope.launch {
            prefsManager.setEmergencyNumber(number)
            // Also save encrypted copy
            secureStorage.saveEmergencyNumber(number)
        }
    }
}

// ─────────────────────────────────────────────
//  Fragment
// ─────────────────────────────────────────────

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Prevent feedback loop: when we programmatically set SeekBar progress,
    // it would trigger the listener and call ViewModel again
    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe settings
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    isUpdatingUi = true
                    binding.seekbarThreshold.progress = state.fallThreshold.toInt()
                    binding.tvThresholdValue.text = "${state.fallThreshold.toInt()} m/s²"
                    binding.switchDarkMode.isChecked = state.darkMode
                    binding.switchSmsSimulation.isChecked = state.smsSimulationMode
                    if (binding.etEmergencyNumber.text.toString() != state.emergencyNumber) {
                        binding.etEmergencyNumber.setText(state.emergencyNumber)
                    }
                    isUpdatingUi = false
                }
            }
        }

        // SeekBar for fall detection threshold (5..30 m/s²)
        binding.seekbarThreshold.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingUi) {
                    val clamped = progress.coerceIn(5, 30).toFloat()
                    binding.tvThresholdValue.text = "$clamped m/s²"
                    viewModel.setFallThreshold(clamped)
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            if (!isUpdatingUi) viewModel.setDarkMode(checked)
        }

        binding.switchSmsSimulation.setOnCheckedChangeListener { _, checked ->
            if (!isUpdatingUi) viewModel.setSmsSimulationMode(checked)
        }

        binding.btnSaveNumber.setOnClickListener {
            val number = binding.etEmergencyNumber.text.toString().filter { it.isDigit() }
            viewModel.setEmergencyNumber(number)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
