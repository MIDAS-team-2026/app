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

    @GET("api/ai/analysis/linguistic-markers/user/{userId}")
    suspend fun getLinguisticMarkers(
        @Path("userId") userId: Int
    ): Response<com.midas26.mobileapp.network.ApiResponse<List<LinguisticMarkerResponse>>>
}

/** 세션 1건의 언어 지표. analyzedAt 최신순으로 내려온다(index 0 = 오늘/최신). */
data class LinguisticMarkerResponse(
    val sessionId: Long?,
    val analyzedAt: String?,
    val pronounNounRatio: Float?,
    val nounRatio: Float?,
    val lexicalDiversityMattr: Float?,
    val repetitionScore: Float?,
    val pronounNounRatioZScore: Float?,
    val nounRatioZScore: Float?,
    val lexicalDiversityMattrZScore: Float?,
    val repetitionScoreZScore: Float?,
    val baselineSampleSize: Int?
)

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
