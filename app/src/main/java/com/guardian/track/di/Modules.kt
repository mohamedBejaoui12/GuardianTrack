package com.guardian.track.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.guardian.track.BuildConfig
import com.guardian.track.data.local.AppDatabase
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.remote.api.GuardianApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * DatabaseModule — provides Room singleton.
 *
 * @InstallIn(SingletonComponent) means these bindings live as long as the app.
 * Hilt will automatically inject AppDatabase wherever it's declared as a parameter.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "guardian_db")
            .fallbackToDestructiveMigration()  // for dev — use proper Migration in production
            .build()

    @Provides
    fun provideIncidentDao(db: AppDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideContactDao(db: AppDatabase): EmergencyContactDao = db.emergencyContactDao()
}

/**
 * NetworkModule — provides Retrofit singleton.
 *
 * The API base URL comes from BuildConfig, which reads local.properties.
 * The logging interceptor prints HTTP traffic to Logcat in debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttp: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): GuardianApi =
        retrofit.create(GuardianApi::class.java)
}

/**
 * AppModule — provides miscellaneous singletons that don't fit the other modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    /**
     * FusedLocationProviderClient is the recommended way to get GPS on Android.
     * It automatically selects the most accurate/power-efficient provider.
     */
    @Provides
    @Singleton
    fun provideFusedLocation(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)
}
