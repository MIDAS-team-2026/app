package com.midas26.mobileapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

/** 보호자↔환자 연동 요청 body */
data class LinkRequest(val protectorId: Int, val patientCode: String)

/** 연결된 사용자 정보 (보호자→환자, 환자→보호자 공용) */
data class LinkedUserInfo(
    val userId: Int?,
    val phone: String?,
    val name: String?,
    val role: String?,
    val patientCode: String?
)

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

    // ── 보호자-환자 연동 ─────────────────────────────────────────────────────

    /** 보호자 기준: 연결된 환자 목록 */
    @GET("api/auth/protectors/{protectorId}/patients")
    suspend fun getPatientsByProtector(
        @Path("protectorId") protectorId: Int
    ): Response<ApiResponse<List<LinkedUserInfo>>>

    /** 환자 기준: 연결된 보호자 목록 */
    @GET("api/auth/patients/{patientId}/guardians")
    suspend fun getGuardiansByPatient(
        @Path("patientId") patientId: Int
    ): Response<ApiResponse<List<LinkedUserInfo>>>

    /** 코드로 환자 정보 조회 (교차검증용) */
    @GET("api/auth/patients/by-code/{code}")
    suspend fun getPatientByCode(
        @Path("code") code: String
    ): Response<ApiResponse<LinkedUserInfo>>

    /** 코드 입력으로 환자 연동 */
    @POST("api/auth/link")
    suspend fun linkPatient(@Body request: LinkRequest): Response<ApiResponse<Unit>>

    /** 연동 해제 */
    @DELETE("api/auth/protectors/{protectorId}/patients/{patientId}")
    suspend fun unlinkPatient(
        @Path("protectorId") protectorId: Int,
        @Path("patientId") patientId: Int
    ): Response<ApiResponse<Unit>>
}
