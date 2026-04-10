package com.guardian.track.data.remote.dto

/**
 * Data Transfer Object — the shape of JSON we send to the API.
 *
 * Why separate from IncidentEntity?
 * The Entity is coupled to Room (column names, types Room understands).
 * The DTO is coupled to the API contract (field names the server expects).
 * If the server renames a field we only change the DTO, not the DB schema.
 * This separation is one concrete benefit of layered architecture.
 */
data class IncidentDto(
    val timestamp: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double
)
