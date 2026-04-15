package com.guardian.track.data.local

// [Summary] Structured and concise implementation file.

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "emergency_detector_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val FALL_THRESHOLD = floatPreferencesKey("emergency_detector_fall_threshold")
        val DARK_MODE = booleanPreferencesKey("emergency_detector_dark_mode")
        val EMERGENCY_NUMBER = stringPreferencesKey("emergency_detector_number")
        val SMS_SIMULATION_MODE = booleanPreferencesKey("emergency_detector_sms_sim_mode")
    }

    private val safePrefsData: Flow<Preferences> = context.dataStore.data.catch { error ->
        if (error is IOException) {
            context.filesDir.resolve("datastore/emergency_detector_prefs.preferences_pb").delete()
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

        val fallThreshold: Flow<Float> = safePrefsData.map { prefs ->
        prefs[FALL_THRESHOLD] ?: 15.0f
    }

    val darkMode: Flow<Boolean> = safePrefsData.map { prefs ->
        prefs[DARK_MODE] ?: false
    }

        val smsSimulationMode: Flow<Boolean> = safePrefsData.map { prefs ->
        prefs[SMS_SIMULATION_MODE] ?: true
    }

    val emergencyNumber: Flow<String> = safePrefsData.map { prefs ->
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
