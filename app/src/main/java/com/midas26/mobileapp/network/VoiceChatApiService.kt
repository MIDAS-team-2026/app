package com.midas26.mobileapp.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** 음성 대화 세션 시작 + 녹음 파일 업로드 + AI 응답 폴링 API. */
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
     * 서버 → Python으로 STT + AI 응답 생성을 비동기 트리거한다.
     * @return audioRecordId — 이후 폴링에 사용
     */
    @Multipart
    @POST("api/voice/upload")
    suspend fun uploadVoice(
        @Query("userId")    userId:    Int,
        @Query("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part,
        @Query("recallQuestionId") recallQuestionId: Long? = null,
        @Query("answerRole") answerRole: String? = null
    ): Response<ApiResponse<VoiceUploadResponse>>

    /**
     * AI 답변 폴링.
     * replyText == null → 아직 생성 중 / replyText != null → 완료
     */
    @GET("api/voice/reply/{recordId}")
    suspend fun pollReply(
        @Path("recordId") recordId: Long
    ): Response<ApiResponse<AiReplyResponse>>

    @GET("api/voice/session/{sessionId}/records")
    suspend fun getSessionRecords(
        @Path("sessionId") sessionId: Long
    ): Response<List<SessionRecordResponse>>

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

data class AiReplyResponse(
    val recordId: Long,
    val replyText: String?   // null = 아직 생성 중
)

data class SessionRecordResponse(
    val recordId: Long,
    val transcriptText: String?,
    val audioFilePath: String?,
    val answerRole: String?,
    val recallQuestionId: Long?,
    val parentRecordId: Long?
)
