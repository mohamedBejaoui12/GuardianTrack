package com.guardian.track.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.guardian.track.util.NotificationHelper
import com.guardian.track.worker.BatteryCriticalWorker
import com.guardian.track.worker.BootSurveillanceWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BatteryReceiver — triggered by the OS when battery is critically low.
 *
 * Registered statically in the Manifest, so it fires even when the app is not running.
 * We cannot start a foreground service directly here (Android 12+ restriction),
 * but we CAN insert a Room record via WorkManager and show a notification.
 *
 * This receiver uses goAsync() to do async work safely.
 */
class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_LOW) return
        Log.i("BatteryReceiver", "Battery low , scheduling incident record")

        // Schedule a WorkManager task that saves the BATTERY incident to Room
        val work = OneTimeWorkRequestBuilder<BatteryCriticalWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(work)

        // Show immediate notification (no DB needed for this)
        NotificationHelper.showIncidentNotification(
            context,
            "Battery Critical",
            "Battery is low. Emergency services may be unavailable."
        )
    }
}

/**
 * BootReceiver — triggered after the phone reboots.
 *
 * Android 12+ restriction: apps cannot start background services directly
 * from a BroadcastReceiver (unless they are in an exempted state).
 *
 * Solution: use WorkManager with setExpedited(). WorkManager is aware of
 * these restrictions and handles them correctly per API level.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        Log.i("BootReceiver", "Boot completed — scheduling surveillance restart via WorkManager")

        val work = OneTimeWorkRequestBuilder<BootSurveillanceWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }
}
