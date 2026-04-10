package com.guardian.track.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property creates a single DataStore instance per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_prefs")

/**
 * Wrapper around DataStore<Preferences>.
 *
 * DataStore is the modern replacement for SharedPreferences.
 * It's coroutine-based, handles concurrent access safely, and
 * stores preferences to disk asynchronously.
 *
 * The emergency phone number is stored here but ENCRYPTED via
 * EncryptedSharedPreferences in SecurityUtil (see SecurityUtil.kt).
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val FALL_THRESHOLD = floatPreferencesKey("fall_threshold")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val EMERGENCY_NUMBER = stringPreferencesKey("emergency_number")
        val SMS_SIMULATION_MODE = booleanPreferencesKey("sms_simulation_mode")
    }

    /** Emits the current fall detection threshold whenever it changes. */
    val fallThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[FALL_THRESHOLD] ?: 15.0f   // default: 15 m/s²
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: false
    }

    /** SMS simulation is ON by default — prevents accidental real SMS in tests. */
    val smsSimulationMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SMS_SIMULATION_MODE] ?: true
    }

    val emergencyNumber: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[EMERGENCY_NUMBER] ?: ""
    }

    suspend fun setFallThreshold(value: Float) {
        context.dataStore.edit { it[FALL_THRESHOLD] = value }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setSmsSimulationMode(enabled: Boolean) {
        context.dataStore.edit { it[SMS_SIMULATION_MODE] = enabled }
    }

    suspend fun setEmergencyNumber(number: String) {
        context.dataStore.edit { it[EMERGENCY_NUMBER] = number }
    }
}
