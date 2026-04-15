package com.guardian.track.model

// [Summary] Structured and concise implementation file.

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Incident(
    val id: Long,
    val formattedDate: String,
    val formattedTime: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val isSynced: Boolean
)

fun Long.toFormattedDate(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))

fun Long.toFormattedTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val message: String, val code: Int? = null) : NetworkResult<T>()
    object Loading : NetworkResult<Nothing>()
}
