package com.guardian.track.receiver

// [Summary] Structured and concise implementation file.

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

class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_LOW) return
        Log.i("BatteryReceiver", "Battery low , scheduling incident record")
        val work = OneTimeWorkRequestBuilder<BatteryCriticalWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(work)
        NotificationHelper.showIncidentNotification(
            context,
            "Battery Critical",
            "Battery is low. Emergency services may be unavailable."
        )
    }
}

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
