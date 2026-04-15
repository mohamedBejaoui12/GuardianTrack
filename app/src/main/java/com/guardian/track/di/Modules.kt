package com.guardian.track.di

// [Summary] Structured and concise implementation file.

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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "guardian_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideIncidentDao(db: AppDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideContactDao(db: AppDatabase): EmergencyContactDao = db.emergencyContactDao()
}

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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

        @Provides
    @Singleton
    fun provideFusedLocation(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)
}
