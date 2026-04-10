package com.guardian.track.di

import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Note: androidx.hilt:hilt-work provides HiltWorkerFactory automatically.
// Add this to your dependencies in build.gradle.kts:
//   implementation("androidx.hilt:hilt-work:1.2.0")
//   ksp("androidx.hilt:hilt-compiler:1.2.0")
//
// Then in GuardianApplication, override getWorkManagerConfiguration():
/*
@HiltAndroidApp
class GuardianApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
*/
// This replaces the default WorkManager initialization so Hilt can inject
// dependencies into Workers. Without this, @HiltWorker won't work.
