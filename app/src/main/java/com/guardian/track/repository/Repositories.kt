package com.guardian.track.repository

// [Summary] Structured and concise implementation file.

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.data.remote.api.GuardianApi
import com.guardian.track.data.remote.dto.EmergencyContactRemoteDto
import com.guardian.track.data.remote.dto.IncidentRemoteDto
import com.guardian.track.model.*
import com.guardian.track.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val api: GuardianApi,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
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

    suspend fun getRemoteIncidents(): List<IncidentRemoteDto> {
        val response = api.getIncidents()
        return if (response.isSuccessful) {
            response.body().orEmpty()
        } else {
            emptyList()
        }
    }

    suspend fun refreshIncidentsFromRemote() {
        val remoteIncidents = getRemoteIncidents()
        remoteIncidents.forEach { remote ->
            val alreadyExists = incidentDao.countBySignature(
                timestamp = remote.timestamp,
                type = remote.type,
                latitude = remote.latitude,
                longitude = remote.longitude
            ) > 0
            if (!alreadyExists) {
                incidentDao.insertIncident(
                    IncidentEntity(
                        timestamp = remote.timestamp,
                        type = remote.type,
                        latitude = remote.latitude,
                        longitude = remote.longitude,
                        isSynced = true
                    )
                )
            }
        }
    }

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
        try {
            val response = api.postIncident(
                IncidentRemoteDto(
                    timestamp = entity.timestamp,
                    type = type,
                    latitude = latitude,
                    longitude = longitude,
                    deviceId = deviceId()
                )
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

    private fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

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
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: EmergencyContactDao,
    private val api: GuardianApi
) {
    fun getAllContacts(): Flow<List<EmergencyContactEntity>> =
        contactDao.getAllContacts()

    suspend fun addContact(name: String, phone: String) {
        contactDao.insertContact(EmergencyContactEntity(name = name, phoneNumber = phone))
        try {
            api.postEmergencyContact(
                EmergencyContactRemoteDto(
                    name = name,
                    number = phone
                )
            )
        } catch (e: Exception) {
            Log.d("ContactRepo", "Remote contact sync skipped: ${e.message}")
        }
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) =
        contactDao.deleteContact(contact)

    suspend fun getRemoteContacts(): List<EmergencyContactRemoteDto> {
        val response = api.getEmergencyContacts()
        return if (response.isSuccessful) {
            response.body().orEmpty()
        } else {
            emptyList()
        }
    }
}
