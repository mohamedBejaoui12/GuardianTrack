package com.guardian.track.data.remote.api

import com.guardian.track.data.remote.dto.IncidentDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service interface.
 * Retrofit generates the HTTP implementation at compile time.
 * 'suspend' makes the function coroutine-friendly — no callbacks needed.
 */
interface GuardianApi {

    /**
     * POST a new incident alert to the remote server.
     * Response<Unit> gives us the HTTP status code without parsing a body.
     */
    @POST("incidents")
    suspend fun postIncident(@Body incident: IncidentDto): Response<Unit>
}
