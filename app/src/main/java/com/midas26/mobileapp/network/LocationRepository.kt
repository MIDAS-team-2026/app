package com.midas26.mobileapp.network

object LocationRepository {

    private val api: LocationApiService = RetrofitClient.location

    suspend fun saveLocation(
        userId: Int,
        latitude: Double,
        longitude: Double
    ): Result<ApiResponse<Unit>> {
        return runCatching {
            api.saveLocation(
                SaveLocationRequest(
                    userId = userId,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }

    suspend fun getCurrentLocation(
        userId: Int
    ): Result<CurrentLocationResponse?> {
        return runCatching {
            api.getCurrentLocation(userId).data
        }
    }

    suspend fun getRoute(
        userId: Int,
        date: String
    ): Result<List<RoutePointResponse>> {
        return runCatching {
            api.getRoute(userId, date).data ?: emptyList()
        }
    }
}