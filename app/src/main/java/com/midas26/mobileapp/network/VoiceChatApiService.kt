package com.midas26.mobileapp.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** 음성 대화 세션 시작 + 녹음 파일 업로드 API. */
interface VoiceChatApiService {

    /**
     * 대화 세션 생성.
     * @return 생성된 sessionId (Long)
     */
    @POST("api/chat/session/start")
    suspend fun startSession(
        @Query("userId") userId: Int
    ): Response<ApiResponse<Long>>

    /**
     * 녹음 WAV 파일 업로드.
     * 서버에서 STT 처리 후 AI 응답을 생성한다 (현재 미구현 — recordId만 반환).
     */
    @Multipart
    @POST("api/voice/upload")
    suspend fun uploadVoice(
        @Query("userId")    userId:    Int,
        @Query("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<VoiceUploadResponse>>

    /**
     * 세션 종료 + AI 분석 트리거.
     * 화면 이탈(뒤로가기, 다른 화면 이동 등) 시 호출한다.
     */
    @POST("api/ai/analysis/chat/session/{sessionId}/complete")
    suspend fun completeSession(
        @Path("sessionId") sessionId: Long
    ): Response<Unit>
}

data class VoiceUploadResponse(
    val audioRecordId: Long,
    val audioFilePath: String,
    val turnOrder: Int,
    val recordedAt: String
)
