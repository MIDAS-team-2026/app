package com.midas26.mobileapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val phone: String, val password: String)
data class SignupRequest(
    val phone: String,
    val password: String,
    val name: String,
    val role: String,
    val ageGroup: Int?,
    val gender: Int?
)
data class ApiResponse<T>(val status: Int, val message: String?, val data: T?)
data class UserResponse(
    val userId: Int?,
    val phone: String?,
    val name: String?,
    val token: String?,
    val role: String?,
    val patientCode: String?
)

interface AuthApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<UserResponse>>

    @POST("api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<UserResponse>>
}
