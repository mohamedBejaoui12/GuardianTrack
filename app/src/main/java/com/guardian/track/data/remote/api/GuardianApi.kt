package com.guardian.track.data.remote.api


import com.guardian.track.data.remote.dto.EmergencyContactRemoteDto
import com.guardian.track.data.remote.dto.IncidentRemoteDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

interface GuardianApi {

    @GET("incidents")
    suspend fun getIncidents(): Response<List<IncidentRemoteDto>>

    @POST("incidents")
    suspend fun postIncident(@Body incident: IncidentRemoteDto): Response<IncidentRemoteDto>

    @DELETE("incidents/{id}")
    suspend fun deleteIncident(@Path("id") id: String): Response<Unit>

    @GET("emergency_contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContactRemoteDto>>

    @POST("emergency_contacts")
    suspend fun postEmergencyContact(@Body contact: EmergencyContactRemoteDto): Response<EmergencyContactRemoteDto>

    @DELETE("emergency_contacts/{id}")
    suspend fun deleteEmergencyContact(@Path("id") id: String): Response<Unit>
}
