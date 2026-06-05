package com.midas26.mobileapp.network

data class SaveLocationRequest(
    val userId: Int,
    val latitude: Double,
    val longitude: Double
)

data class SafeZoneRequest(
    val userId: Int,
    val zoneName: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double
)

data class CurrentLocationResponse(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String
)

data class RoutePointResponse(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String
)