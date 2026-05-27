package com.midas26.mobileapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface AnalysisApiService {

    /**
     * 세션 분석 요약 조회.
     * GET /api/ai/analysis/session/{sessionId}/summary
     */
    @GET("api/ai/analysis/session/{sessionId}/summary")
    suspend fun getSessionSummary(
        @Path("sessionId") sessionId: Long
    ): Response<SessionSummaryResponse>
}

data class SessionSummaryResponse(
    val sessionId: Long?,
    val finalRiskScore: Float?,
    val riskLevel: String?,
    val speechScore: Float?,
    val textScore: Float?,
    val recallScore: Float?,
    val analyzedAt: String?
)
