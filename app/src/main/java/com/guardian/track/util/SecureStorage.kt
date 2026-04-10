package com.guardian.track.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps EncryptedSharedPreferences for storing sensitive values.
 *
 * Uses AES256-GCM encryption via the Android Keystore.
 * The master key is stored in the hardware-backed Keystore (on supported devices)
 * and never leaves the secure enclave.
 *
 * Used for:
 *  - Emergency phone number
 *  - API key (if you add one)
 *
 * NOTE: DataStore (PreferencesManager) stores the phone number as a plain string
 * for convenience in Flows. For the actually-encrypted copy, use this class.
 * In a production app you'd use only this class and wrap it in a Flow yourself.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "guardian_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) = prefs.edit().putString("api_key", key).apply()
    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""

    fun saveEmergencyNumber(number: String) = prefs.edit().putString("emergency_phone", number).apply()
    fun getEmergencyNumber(): String = prefs.getString("emergency_phone", "") ?: ""
}
