package com.guardian.track.service

import android.app.*
import android.content.Context
import android.content.Intent
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
 */
@AndroidEntryPoint  // Required for Hilt to inject into a Service
class SurveillanceService : Service() {

    @Inject lateinit var incidentRepository: IncidentRepository
    @Inject lateinit var fusedLocation: FusedLocationProviderClient
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var smsHelper: SmsHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // HandlerThread: dedicated background thread for sensor callbacks
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    // Fall detection state machine
    private var freeFallStartTime: Long = 0L
    private var inFreeFall = false
    private var fallThreshold = 15.0f  // m/s², updated from DataStore

    companion object {
        const val CHANNEL_ID = "guardian_surveillance"
        const val NOTIFICATION_ID = 1
        const val FREEFALL_MAGNITUDE = 3.0f      // m/s² — below this = free fall
        const val FREEFALL_DURATION_MS = 100L    // must stay in free fall this long
        const val IMPACT_WINDOW_MS = 200L        // impact must follow within this window
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
        startForeground(NOTIFICATION_ID, buildNotification())

        // Read threshold from DataStore once on start
        serviceScope.launch {
            fallThreshold = preferencesManager.fallThreshold.first()
        }

        setupAccelerometer()
    }

    /**
     * Sets up a dedicated HandlerThread for sensor events.
     * This prevents sensor callbacks from competing with the UI thread.
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
                SensorManager.SENSOR_DELAY_GAME,  // ~20ms polling — enough for fall detection
                sensorHandler                      // deliver events on sensorThread
            )
        } ?: Log.w(TAG, "No accelerometer found on device")
    }

    /**
     * SensorEventListener implementation.
     * This runs on sensorThread (HandlerThread), NOT the main thread.
     */
    private val sensorEventListener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // Magnitude = vector length of (ax, ay, az)
            val magnitude = sqrt(ax * ax + ay * ay + az * az)
            val now = System.currentTimeMillis()

            when {
                // Phase 1: detect free fall
                magnitude < FREEFALL_MAGNITUDE && !inFreeFall -> {
                    inFreeFall = true
                    freeFallStartTime = now
                }

                // Still in free fall — check duration
                magnitude < FREEFALL_MAGNITUDE && inFreeFall -> {
                    // nothing extra needed — timing tracked by freeFallStartTime
                }

                // Phase 2: detect impact after sufficient free-fall duration
                magnitude > fallThreshold && inFreeFall -> {
                    val freeFallDuration = now - freeFallStartTime
                    if (freeFallDuration >= FREEFALL_DURATION_MS &&
                        freeFallDuration <= (FREEFALL_DURATION_MS + IMPACT_WINDOW_MS)) {
                        // FALL CONFIRMED
                        inFreeFall = false
                        onFallDetected()
                    } else {
                        inFreeFall = false
                    }
                }

                // Exit free fall without impact (false alarm)
                magnitude > FREEFALL_MAGNITUDE && inFreeFall -> {
                    inFreeFall = false
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Called when a fall is confirmed.
     * Switches to IO dispatcher to do network/DB work off the sensor thread.
     */
    private fun onFallDetected() {
        Log.i(TAG, "FALL DETECTED")
        serviceScope.launch(Dispatchers.IO) {
            // Get last known location (best-effort)
            val (lat, lon) = getLastLocation()

            // Save to Room + attempt sync
            incidentRepository.saveAndSync("FALL", lat, lon)

            // Send SMS alert (simulated or real based on settings)
            val phone = preferencesManager.emergencyNumber.first()
            val simMode = preferencesManager.smsSimulationMode.first()
            smsHelper.sendAlert(phone, "FALL", simMode, this@SurveillanceService)

            // Show a local notification
            NotificationHelper.showIncidentNotification(
                this@SurveillanceService,
                "Fall Detected",
                "A fall was detected and an alert has been sent."
            )
        }
    }

    /**
     * Gets last known GPS location.
     * If permission was denied, returns 0.0 / 0.0 (sentinel values — spec §4.1).
     */
    @Suppress("MissingPermission")
    private suspend fun getLastLocation(): Pair<Double, Double> =
        try {
            val task = fusedLocation.lastLocation
            // kotlinx-coroutines-play-services adds await() to Task<T>
            val loc = task.await()
            Pair(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if killed by OS, restart service automatically
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null  // not a bound service

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
            NotificationManager.IMPORTANCE_LOW  // LOW = no sound, just persistent icon
        ).apply {
            description = "GuardianTrack active monitoring"
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
            .setContentTitle("GuardianTrack Active")
            .setContentText("Monitoring for falls and alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openAppIntent)
            .setOngoing(true)   // user cannot swipe away
            .build()
    }
}
