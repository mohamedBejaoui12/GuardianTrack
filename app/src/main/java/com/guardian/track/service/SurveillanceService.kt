package com.guardian.track.service

// [Summary] Structured and concise implementation file.

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.guardian.track.R
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.ui.MainActivity
import com.guardian.track.util.NotificationHelper
import com.guardian.track.util.SmsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class SurveillanceService : Service() {

    @Inject lateinit var incidentRepository: IncidentRepository
    @Inject lateinit var fusedLocation: FusedLocationProviderClient
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var smsHelper: SmsHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    private var freeFallStartTime: Long = 0L
    private var inFreeFall = false
    private var lastDetectionAt: Long = 0L
    @Volatile
    private var fallThreshold = 15.0f

    companion object {
        const val CHANNEL_ID = "emergency_detector_surveillance"
        const val NOTIFICATION_ID = 1
        const val FREEFALL_MAGNITUDE = 3.0f
        const val FREEFALL_DURATION_MS = 100L
        const val IMPACT_WINDOW_MS = 200L
        const val DETECTION_COOLDOWN_MS = 2500L
        private const val TAG = "SurveillanceService"

        fun startService(context: Context) {
            val intent = Intent(context, SurveillanceService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SurveillanceService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        serviceScope.launch {
            preferencesManager.fallThreshold.collectLatest { latestThreshold ->
                fallThreshold = latestThreshold
                Log.d(TAG, "Updated fall threshold=$fallThreshold")
            }
        }

        setupAccelerometer()
    }

        private fun setupAccelerometer() {
        sensorThread = HandlerThread("SensorThread").apply { start() }
        sensorHandler = Handler(sensorThread.looper)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                sensorHandler
            )
        } ?: Log.w(TAG, "No accelerometer found on device")
    }

        private val sensorEventListener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val magnitude = sqrt(ax * ax + ay * ay + az * az)
            val now = System.currentTimeMillis()

            when {
                magnitude < FREEFALL_MAGNITUDE && !inFreeFall -> {
                    inFreeFall = true
                    freeFallStartTime = now
                }
                magnitude < FREEFALL_MAGNITUDE && inFreeFall -> {
                    if (now - freeFallStartTime > (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS)) {
                        inFreeFall = false
                    }
                }

                inFreeFall -> {
                    val freeFallDuration = now - freeFallStartTime

                    when {
                        magnitude > fallThreshold &&
                            freeFallDuration >= FREEFALL_DURATION_MS &&
                            freeFallDuration <= (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS) -> {
                            inFreeFall = false
                            if (now - lastDetectionAt >= DETECTION_COOLDOWN_MS) {
                                lastDetectionAt = now
                                onFallDetected()
                            }
                        }
                        magnitude > FREEFALL_MAGNITUDE && freeFallDuration < FREEFALL_DURATION_MS -> {
                            inFreeFall = false
                        }
                        freeFallDuration > (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS) -> {
                            inFreeFall = false
                        }
                        else -> Unit
                    }
                }

                else -> {
                    if (inFreeFall) {
                        inFreeFall = false
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

        private fun onFallDetected() {
        Log.i(TAG, "FALL DETECTED")
        serviceScope.launch(Dispatchers.IO) {
            val (lat, lon) = getLastLocation()
            incidentRepository.saveAndSync("FALL", lat, lon)

            val phone = preferencesManager.emergencyNumber.first()
            val simMode = preferencesManager.smsSimulationMode.first()
            smsHelper.sendAlert(phone, "FALL", simMode, this@SurveillanceService)

            NotificationHelper.showIncidentNotification(
                this@SurveillanceService,
                "Emergency Detected",
                "Emergency Detector identified a possible fall and sent an alert."
            )
        }
    }

        @Suppress("MissingPermission")
    private suspend fun getLastLocation(): Pair<Double, Double> =
        try {
            val loc = fusedLocation.lastLocation.await()
            Pair(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(sensorEventListener)
        sensorThread.quitSafely()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Emergency Surveillance",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background emergency detection service"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency Detector Active")
            .setContentText("Detecting falls and preparing emergency alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}