package com.guardian.track.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardian.track.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes all notification logic.
 * Using object (singleton) here because it's stateless and called from
 * places like BroadcastReceiver that don't have Hilt injection.
 */
object NotificationHelper {

    private const val INCIDENT_CHANNEL_ID = "guardian_incidents"
    private var notifId = 100

    fun showIncidentNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel if needed (idempotent — safe to call multiple times)
        val channel = NotificationChannel(
            INCIDENT_CHANNEL_ID,
            "Incident Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent safety alerts from GuardianTrack"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300)
            // Set default alarm sound as ringtone for high-urgency alerts
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(alarmSound, audioAttrs)
        }
        manager.createNotificationChannel(channel)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, INCIDENT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 300, 200, 300, 200, 300))
            .setOnlyAlertOnce(false)
            .build()

        manager.notify(notifId++, notification)
    }
}

/**
 * Handles emergency SMS sending.
 *
 * SIMULATION MODE (default ON):
 *   Logs the SMS to console and shows a notification instead of actually sending.
 *   This prevents accidental SMS in academic/testing environments.
 *
 * REAL MODE:
 *   Uses SmsManager.sendTextMessage(). Requires SEND_SMS permission granted at runtime.
 */
@Singleton
class SmsHelper @Inject constructor() {

    companion object {
        private const val TAG = "SmsHelper"
        private const val MAX_SMS_LENGTH = 160
    }

    fun sendAlert(
        phoneNumber: String,
        incidentType: String,
        simulationMode: Boolean,
        context: Context
    ) {
        val message = buildMessage(incidentType)

        if (simulationMode) {
            // SIMULATION: log + notification, no real SMS
            Log.i(TAG, "📱 [SIMULATION] SMS to $phoneNumber: $message")
            NotificationHelper.showIncidentNotification(
                context,
                "SMS Simulated",
                "Would send to $phoneNumber: $message"
            )
            return
        }

        // REAL SMS — only executes if user explicitly disabled simulation mode
        if (phoneNumber.isBlank()) {
            Log.w(TAG, "No emergency number configured — SMS not sent")
            return
        }

        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            // splitMessage handles messages > 160 chars automatically
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.i(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}")
        }
    }

    private fun buildMessage(incidentType: String): String {
        val timeStr = java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "GuardianTrack ALERT: $incidentType incident at $timeStr. Please check on me."
    }
}
