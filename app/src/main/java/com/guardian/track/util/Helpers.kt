package com.guardian.track.util

// [Summary] Structured and concise implementation file.

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

object NotificationHelper {

    private const val INCIDENT_CHANNEL_ID = "emergency_detector_incidents"
    private var notifId = 100

    fun showIncidentNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            INCIDENT_CHANNEL_ID,
            "Incident Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent safety alerts from Emergency Detector"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300)
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

fun buildSmsAlertMessage(incidentType: String, timestampMillis: Long = System.currentTimeMillis()): String {
    val timeStr = java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault())
        .format(java.util.Date(timestampMillis))
    return "Emergency Detector ALERT: $incidentType incident at $timeStr. Please check on me."
}

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
        val message = buildSmsAlertMessage(incidentType)

        if (simulationMode) {
            Log.i(TAG, "📱 [SIMULATION] SMS to $phoneNumber: $message")
            NotificationHelper.showIncidentNotification(
                context,
                "SMS Simulated",
                "Would send to $phoneNumber: $message"
            )
            return
        }
        if (phoneNumber.isBlank()) {
            Log.w(TAG, "No emergency number configured — SMS not sent")
            return
        }

        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.i(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}")
        }
    }

}
