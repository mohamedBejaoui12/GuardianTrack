package com.guardian.track.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo         // NEW: for FOREGROUND_SERVICE_TYPE_*
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

/**
 * SurveillanceService — Foreground Service
 *
 * A Foreground Service keeps running even when the user navigates away.
 * It MUST show a persistent notification — this is Android's way of telling
 * the user "this app is actively running in the background."
 *
 * Fall detection algorithm (two-phase):
 *   Phase 1 (Free-fall): magnitude < 3 m/s² for > 100ms   → device is falling
 *   Phase 2 (Impact):    magnitude > threshold within 200ms → device hit the ground
 *
 * Sensor callbacks fire on a HandlerThread (background thread) to avoid
 * blocking the main thread. We then switch to the main dispatcher for UI updates.
 *
 * foregroundServiceType must be declared explicitly on Android 14+ (API 34+):
 *   - FOREGROUND_SERVICE_TYPE_LOCATION : access GPS in background
 *   - FOREGROUND_SERVICE_TYPE_HEALTH   : use high-rate sensors (accelerometer)
 */
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
        const val CHANNEL_ID = "guardian_surveillance"
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

        // On Android 14+ (API 34) we MUST pass the foreground service type(s)
        // explicitly. Using the 3-arg overload prevents the SecurityException
        // that targetSdk 36 throws when the type is inferred incorrectly.
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
            // Keep threshold in sync while service is alive so Settings changes apply immediately.
            preferencesManager.fallThreshold.collectLatest { latestThreshold ->
                fallThreshold = latestThreshold
                Log.d(TAG, "Updated fall threshold=$fallThreshold")
            }
        }

        setupAccelerometer()
    }

    /**
     * Sets up a dedicated HandlerThread for sensor events.
     * Prevents sensor callbacks from competing with the UI thread.
     */
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

    /**
     * SensorEventListener — runs on sensorThread, NOT the main thread.
     */
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
                    // If this low-g phase lasts too long, reset and wait for a fresh sequence.
                    if (now - freeFallStartTime > (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS)) {
                        inFreeFall = false
                    }
                }

                inFreeFall -> {
                    val freeFallDuration = now - freeFallStartTime

                    when {
                        // Expected sequence: sustained free-fall, then impact over threshold.
                        magnitude > fallThreshold &&
                            freeFallDuration >= FREEFALL_DURATION_MS &&
                            freeFallDuration <= (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS) -> {
                            inFreeFall = false
                            if (now - lastDetectionAt >= DETECTION_COOLDOWN_MS) {
                                lastDetectionAt = now
                                onFallDetected()
                            }
                        }

                        // Free-fall was too brief; likely movement noise.
                        magnitude > FREEFALL_MAGNITUDE && freeFallDuration < FREEFALL_DURATION_MS -> {
                            inFreeFall = false
                        }

                        // Impact window expired without strong hit.
                        freeFallDuration > (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS) -> {
                            inFreeFall = false
                        }

                        // Keep waiting briefly for a possible impact.
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

    /**
     * Called when a fall is confirmed.
     * Switches to IO dispatcher for network/DB work.
     */
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
                "Emergancey Detected",
                "The Guardin detect an Emergency, We've sent an alert."
            )
        }
    }

    /**
     * Gets last known GPS location.
     * Returns 0.0 / 0.0 if permission denied or location unavailable.
     */
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

    // ===== Notification helpers =====

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Surveillance",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Guardian Track Watch "
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
            .setContentTitle("Guardian Track In Watch")
            .setContentText("Monitoring for falls and alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}