package com.guardian.track.data.local.dao

// [Summary] Structured and concise implementation file.

import androidx.room.*
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity): Long

        @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

        @Query("SELECT * FROM incidents WHERE isSynced = 0")
    suspend fun getUnsyncedIncidents(): List<IncidentEntity>

        @Query("UPDATE incidents SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

        @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    suspend fun getAllIncidentsOnce(): List<IncidentEntity>

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT COUNT(*) FROM incidents WHERE timestamp = :timestamp AND type = :type AND latitude = :latitude AND longitude = :longitude"
    )
    suspend fun countBySignature(
        timestamp: Long,
        type: String,
        latitude: Double,
        longitude: Double
    ): Int
}

@Dao
interface EmergencyContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

        @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContactsSync(): List<EmergencyContactEntity>

    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    fun getContactByIdSync(id: Long): EmergencyContactEntity?
}
