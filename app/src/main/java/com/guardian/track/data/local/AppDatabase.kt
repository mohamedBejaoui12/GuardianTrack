package com.guardian.track.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity

/**
 * Room database — single source of truth for all local data.
 *
 * @Database lists every entity (= table) and the schema version.
 * When you add/change columns you must increment version and provide a Migration.
 *
 * The actual database instance is created once (singleton) by Hilt in DatabaseModule.
 */
@Database(
    entities = [IncidentEntity::class, EmergencyContactEntity::class],
    version = 1,
    exportSchema = false    // set true in production to export schema for migrations
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun emergencyContactDao(): EmergencyContactDao
}
