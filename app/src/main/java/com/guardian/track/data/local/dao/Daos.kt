package com.guardian.track.data.local.dao

import androidx.room.*
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity): Long

    /** Emits a new list every time the incidents table changes. */
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    /** Used by WorkManager to find records that still need to be sent. */
    @Query("SELECT * FROM incidents WHERE isSynced = 0")
    suspend fun getUnsyncedIncidents(): List<IncidentEntity>

    /** Called after a successful Retrofit POST to mark the record as sent. */
    @Query("UPDATE incidents SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    /** Used for CSV export — returns all records once (no live updates needed). */
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    suspend fun getAllIncidentsOnce(): List<IncidentEntity>

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * Data Access Object for emergency contacts.
 * Also used by EmergencyContactProvider (ContentProvider).
 */
@Dao
interface EmergencyContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    /** Synchronous version for ContentProvider (runs on its own thread). */
    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContactsSync(): List<EmergencyContactEntity>

    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    fun getContactByIdSync(id: Long): EmergencyContactEntity?
}
