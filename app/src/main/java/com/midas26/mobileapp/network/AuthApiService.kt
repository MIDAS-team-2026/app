package com.midas26.mobileapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

data class ForgotPasswordRequest(val phone: String)
data class ResetPasswordRequest(val phone: String, val newPassword: String)
data class WithdrawRequest(val phone: String)
data class SendCodeRequest(val phone: String, val purpose: String)
data class VerifyCodeRequest(val phone: String, val code: String)

interface AuthApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<UserResponse>>

    @POST("api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<UserResponse>>

    @POST("api/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<ApiResponse<Any>>

    @POST("api/auth/verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<ApiResponse<Any>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<Any>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Any>>

    @POST("api/auth/withdraw")
    suspend fun withdraw(@Body request: WithdrawRequest): Response<ApiResponse<Any>>

    // 보호자 기준: 연결된 환자 목록
    @GET("api/auth/protectors/{protectorId}/patients")
    suspend fun getPatientsByProtector(
        @Path("protectorId") protectorId: Int
    ): Response<ApiResponse<List<LinkedUserInfo>>>

    // 환자 기준: 연결된 보호자 목록
    @GET("api/auth/patients/{patientId}/guardians")
    suspend fun getGuardiansByPatient(
        @Path("patientId") patientId: Int
    ): Response<ApiResponse<List<LinkedUserInfo>>>
}

data class LinkedUserInfo(
    val userId: Int?,
    val phone: String?,
    val name: String?,
    val role: String?,
    val patientCode: String?
)
