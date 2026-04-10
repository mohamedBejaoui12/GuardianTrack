package com.guardian.track.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Domain model — the UI layer's view of an incident.
 * Decoupled from Room (no @Entity) and from the API (no JSON annotations).
 *
 * The Repository maps Entity → DomainModel before passing data to the ViewModel.
 * This means if the DB schema changes, only the mapping code changes — not the UI.
 */
data class Incident(
    val id: Long,
    val formattedDate: String,   // pre-formatted for display
    val formattedTime: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val isSynced: Boolean
)

/** Maps a raw timestamp to display strings once, in the Repository. */
fun Long.toFormattedDate(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))

fun Long.toFormattedTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))

/**
 * Sealed class to represent every possible state of a network operation.
 *
 * Sealed classes guarantee exhaustive when() expressions — the compiler
 * forces the UI to handle Loading, Success AND Error states. This prevents
 * the common bug of showing stale data silently when a request fails.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val message: String, val code: Int? = null) : NetworkResult<T>()
    object Loading : NetworkResult<Nothing>()
}
