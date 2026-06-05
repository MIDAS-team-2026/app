package com.midas26.mobileapp.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LocationApiService {

    @POST("api/location")
    suspend fun saveLocation(
        @Body body: SaveLocationRequest
    ): ApiResponse<Unit>

    @POST("api/location/safezone")
    suspend fun saveSafeZone(
        @Body body: SafeZoneRequest
    ): ApiResponse<Unit>

    @GET("api/location/current/{userId}")
    suspend fun getCurrentLocation(
        @Path("userId") userId: Int
    ): ApiResponse<CurrentLocationResponse>

    @GET("api/location/route/{userId}")
    suspend fun getRoute(
        @Path("userId") userId: Int,
        @Query("date") date: String
    ): ApiResponse<List<RoutePointResponse>>

    @GET("api/location/check-safezone/{userId}")
    suspend fun checkSafeZone(
        @Path("userId") userId: Int
    ): ApiResponse<Boolean>
}