package com.guardian.track.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.data.remote.api.GuardianApi
import com.guardian.track.data.remote.dto.IncidentDto
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.service.SurveillanceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * SyncWorker — runs when network becomes available to upload pending incidents.
 *
 * @HiltWorker: Hilt needs special treatment for Workers because WorkManager
 * creates them — not Hilt. @HiltWorker + @AssistedInject is the official pattern.
 *
 * WorkManager guarantees this runs even if the app is killed, as long as
 * the network constraint is eventually satisfied.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val incidentDao: IncidentDao,
    private val api: GuardianApi
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val unsynced = incidentDao.getUnsyncedIncidents()
            Log.d("SyncWorker", "Syncing ${unsynced.size} incidents")

            var allSuccess = true
            for (incident in unsynced) {
                val response = api.postIncident(
                    IncidentDto(incident.timestamp, incident.type, incident.latitude, incident.longitude)
                )
                if (response.isSuccessful) {
                    incidentDao.markAsSynced(incident.id)
                } else {
                    allSuccess = false
                    Log.w("SyncWorker", "Failed for id=${incident.id}, code=${response.code()}")
                }
            }
            if (allSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync error: ${e.message}")
            Result.retry()  // WorkManager will try again later
        }
    }

    // Required for WorkManager to report expedited work's foreground info
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(2, createNotification())

    private fun createNotification() =
        android.app.Notification.Builder(applicationContext, SurveillanceService.CHANNEL_ID)
            .setContentTitle("Syncing incidents...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
}

/**
 * BatteryCriticalWorker — saves a BATTERY incident to Room.
 * Called by BatteryReceiver which cannot access Room directly.
 */
@HiltWorker
class BatteryCriticalWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val incidentRepository: IncidentRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            incidentRepository.saveAndSync("BATTERY", 0.0, 0.0)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(3, createNotification())

    private fun createNotification() =
        android.app.Notification.Builder(applicationContext, SurveillanceService.CHANNEL_ID)
            .setContentTitle("Recording battery incident...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .build()
}

/**
 * BootSurveillanceWorker — restarts the SurveillanceService after device boot.
 *
 * This is the Android 12+ compliant solution. We cannot start a foreground
 * service from a BroadcastReceiver on API 31+. WorkManager handles the
 * platform restrictions and starts the service when conditions are met.
 */
@HiltWorker
class BootSurveillanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            SurveillanceService.startService(applicationContext)
            Log.i("BootWorker", "SurveillanceService restarted after boot")
            Result.success()
        } catch (e: Exception) {
            Log.e("BootWorker", "Failed to start service: ${e.message}")
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(4, createNotification())

    private fun createNotification() =
        android.app.Notification.Builder(applicationContext, SurveillanceService.CHANNEL_ID)
            .setContentTitle("GuardianTrack starting...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .build()
}
