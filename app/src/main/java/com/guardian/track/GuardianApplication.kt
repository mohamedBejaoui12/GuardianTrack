package com.guardian.track

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * @HiltAndroidApp triggers Hilt's code generation and sets up the root DI component.
 *
 * Implements Configuration.Provider so WorkManager uses Hilt's WorkerFactory.
 * This is required for @HiltWorker — without it, WorkManager creates Workers
 * via reflection and can't inject their dependencies.
 */
@HiltAndroidApp
class GuardianApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
