package com.guardian.track.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = createEncryptedPrefs()

    private fun buildMasterKey() = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private fun createEncryptedPrefs() = try {
        openEncryptedPrefs()
    } catch (e: Exception) {
        // The Keystore key was lost (reinstall, restore from backup, OS update, etc.)
        // The encrypted file is now unreadable - wipe it and start fresh.
        context.deleteSharedPreferences("emergency_detector_secure_prefs")
        try {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            ks.deleteEntry("_androidx_security_master_key")
        } catch (_: Exception) {}
        openEncryptedPrefs()
    }

    private fun openEncryptedPrefs() = EncryptedSharedPreferences.create(
        context,
        "emergency_detector_secure_prefs",
        buildMasterKey(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) = prefs.edit().putString("emergency_detector_api_key", key).apply()
    fun getApiKey(): String = prefs.getString("emergency_detector_api_key", "") ?: ""

    fun saveEmergencyNumber(number: String) = prefs.edit().putString("emergency_detector_phone", number).apply()
    fun getEmergencyNumber(): String = prefs.getString("emergency_detector_phone", "") ?: ""
}
