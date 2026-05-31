package com.midas26.mobileapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AnalysisApiService {

    @GET("api/ai/analysis/session/{sessionId}/summary")
    suspend fun getSessionSummary(
        @Path("sessionId") sessionId: Long
    ): Response<SessionSummaryResponse>

    @GET("api/ai/analysis/user/{userId}/latest")
    suspend fun getLatestSummary(
        @Path("userId") userId: Int
    ): Response<com.midas26.mobileapp.network.ApiResponse<SessionSummaryResponse>>

    @GET("api/ai/analysis/user/{userId}/today")
    suspend fun getTodaySummary(
        @Path("userId") userId: Int
    ): Response<com.midas26.mobileapp.network.ApiResponse<SessionSummaryResponse>>

    @GET("api/ai/analysis/user/{userId}/streak")
    suspend fun getStreakDays(
        @Path("userId") userId: Int
    ): Response<com.midas26.mobileapp.network.ApiResponse<Int>>

    @GET("api/ai/analysis/user/{userId}/weekly")
    suspend fun getWeeklyScores(
        @Path("userId") userId: Int
    ): Response<com.midas26.mobileapp.network.ApiResponse<List<DailyScoreResponse>>>

    @GET("api/ai/analysis/user/{userId}/daily")
    suspend fun getDailyScore(
        @Path("userId") userId: Int,
        @Query("date") date: String
    ): Response<com.midas26.mobileapp.network.ApiResponse<DailyScoreResponse>>
}

data class DailyScoreResponse(
    val date: String?,
    val finalRiskScore: Float?,
    val riskLevel: String?,
    val speechScore: Float?,
    val textScore: Float?,
    val recallScore: Float?,
    val sessionCount: Int?
)

data class SessionSummaryResponse(
    val sessionId: Long?,
    val finalRiskScore: Float?,
    val riskLevel: String?,
    val speechScore: Float?,
    val textScore: Float?,
    val recallScore: Float?,
    val analyzedAt: String?
)
