package com.guardian.track.data.remote.dto

// [Summary] Structured and concise implementation file.

data class IncidentRemoteDto(
    val id: String? = null,
    val timestamp: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val deviceId: String? = null
)