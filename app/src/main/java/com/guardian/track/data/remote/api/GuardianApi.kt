package com.guardian.track.data.remote.api

// [Summary] Structured and concise implementation file.

import com.guardian.track.data.remote.dto.IncidentDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GuardianApi {

        @POST("incidents")
    suspend fun postIncident(@Body incident: IncidentDto): Response<Unit>
}
