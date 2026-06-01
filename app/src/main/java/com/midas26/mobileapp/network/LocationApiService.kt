package com.midas26.mobileapp.network

import retrofit2.http.Body
import retrofit2.http.POST

data class LocationRequest(
    val userId: Int,
    val latitude: Double,
    val longitude: Double
)

interface LocationApiService {
    @POST("api/location")
    suspend fun saveLocation(@Body body: LocationRequest)
}
