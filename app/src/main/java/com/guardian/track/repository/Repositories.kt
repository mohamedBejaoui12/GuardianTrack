package com.guardian.track.repository

import android.content.Context
import android.util.Log
import androidx.work.*
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.data.remote.api.GuardianApi
import com.guardian.track.data.remote.dto.IncidentDto
import com.guardian.track.model.*
import com.guardian.track.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository — single source of truth for incident data.
 *
 * The Repository pattern hides the complexity of "where does data come from?"
 * from the ViewModel. The ViewModel says "give me incidents" — it doesn't
 * care whether they come from Room, Retrofit, or a cache.
 *
 * OFFLINE-FIRST strategy:
 * 1. Always save to Room first (so data is never lost).
 * 2. Try Retrofit immediately if network is available.
 * 3. If Retrofit fails, schedule WorkManager to retry when connected.
 */
@Singleton
class IncidentRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val api: GuardianApi,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    /**
     * Returns a Flow of domain models.
     * The .map{} transforms List<Entity> → List<Incident> before the ViewModel sees it.
     * Room emits automatically whenever the table changes.
     */
    fun getAllIncidents(): Flow<List<Incident>> =
        incidentDao.getAllIncidents().map { entities ->
            entities.map { entity ->
                Incident(
                    id = entity.id,
                    formattedDate = entity.timestamp.toFormattedDate(),
                    formattedTime = entity.timestamp.toFormattedTime(),
                    type = entity.type,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    isSynced = entity.isSynced
                )
            }
        }

    /**
     * Saves an incident then attempts immediate sync.
     * If sync fails → schedules WorkManager for later.
     */
    suspend fun saveAndSync(
        type: String,
        latitude: Double,
        longitude: Double
    ) {
        val entity = IncidentEntity(
            timestamp = System.currentTimeMillis(),
            type = type,
            latitude = latitude,
            longitude = longitude,
            isSynced = false
        )
        val id = incidentDao.insertIncident(entity)

        // Attempt immediate upload
        try {
            val response = api.postIncident(
                IncidentDto(entity.timestamp, type, latitude, longitude)
            )
            if (response.isSuccessful) {
                incidentDao.markAsSynced(id)
            } else {
                scheduleSyncWork()
            }
        } catch (e: Exception) {
            Log.d("IncidentRepo", "Network unavailable, scheduling sync: ${e.message}")
            scheduleSyncWork()
        }
    }

    suspend fun deleteIncident(id: Long) = incidentDao.deleteById(id)

    suspend fun getAllForExport() = incidentDao.getAllIncidentsOnce()

    /**
     * Schedules a WorkManager task that runs when network becomes available.
     * setExpedited() hints that this is high-priority but not exact-time-critical.
     */
    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "sync_incidents",
            ExistingWorkPolicy.KEEP,  // don't queue duplicates
            request
        )
    }
}

/**
 * Repository for emergency contacts.
 * Simple CRUD — no network sync needed for contacts.
 */
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: EmergencyContactDao
) {
    fun getAllContacts(): Flow<List<EmergencyContactEntity>> =
        contactDao.getAllContacts()

    suspend fun addContact(name: String, phone: String) {
        contactDao.insertContact(EmergencyContactEntity(name = name, phoneNumber = phone))
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) =
        contactDao.deleteContact(contact)
}
