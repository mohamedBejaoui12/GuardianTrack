package com.guardian.track.data.remote.dto

// [Summary] Structured and concise implementation file.

data class IncidentDto(
    val timestamp: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double
)
