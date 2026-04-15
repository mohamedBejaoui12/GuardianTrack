package com.guardian.track.data.local

// [Summary] Structured and concise implementation file.

import androidx.room.Database
import androidx.room.RoomDatabase
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity

@Database(
    entities = [IncidentEntity::class, EmergencyContactEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun emergencyContactDao(): EmergencyContactDao
}
