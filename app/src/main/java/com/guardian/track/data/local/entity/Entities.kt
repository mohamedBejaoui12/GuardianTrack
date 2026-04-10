package com.guardian.track.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one security incident stored in Room.
 *
 * Fields:
 *  - id: auto-generated primary key
 *  - timestamp: epoch millis when incident occurred
 *  - type: one of FALL | BATTERY | MANUAL
 *  - latitude/longitude: GPS coords (0.0 if permission denied — sentinel value)
 *  - isSynced: false until successfully sent to the remote API
 */
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,          // "FALL" | "BATTERY" | "MANUAL"
    val latitude: Double,
    val longitude: Double,
    val isSynced: Boolean = false
)

/**
 * Emergency contact stored in Room and exposed via EmergencyContactProvider.
 *
 * Column names match what the ContentProvider contract declares (_id is the
 * standard Android column name for provider primary keys).
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String     // digits only
)
