package com.midas26.mobileapp.ui.voicechat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.util.PrefsManager
import com.midas26.mobileapp.util.WavRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/** 채팅 메시지 보낸 사람. */
enum class Sender { AI, USER }

/**
 * 채팅 한 줄.
 * - [isLoading]  : "AI 입력 중" 점 3개 표시
 * - [isSpeaking] : TTS 재생 중 — 말풍선에 음파 인디케이터
 */
data class ChatMessage(
    val from: Sender,
    val text: String,
    val isLoading: Boolean  = false,
    val isSpeaking: Boolean = false
)

/**
 * 음성 대화 화면 상태 머신.
 */
sealed interface VoiceChatState {
    data object Idle       : VoiceChatState
    data object Recording  : VoiceChatState
    data object Processing : VoiceChatState   // 업로드 + 서버 STT/AI 대기
    data object Playing    : VoiceChatState
}

/**
 * 음성 대화 ViewModel.
 *
 * - 화면 진입 시 대화 세션을 서버에서 생성한다.
 * - 녹음 중지 → WAV 파일 → 서버 업로드 → Processing 유지
 * - AI 응답 수신 엔드포인트는 미구현 → [dispatchAiReply]로 나중에 연결
 */
class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs      = PrefsManager.from(application)
    private val wavRecorder = WavRecorder(application)
    private val api        = RetrofitClient.voiceChat

    private val userId: Int get() = prefs.getUserId()
    private var sessionId: Long   = -1L

    // ── 상태 ──────────────────────────────────────────────────────────────────

    private var _state by mutableStateOf<VoiceChatState>(VoiceChatState.Idle)
    val state: VoiceChatState get() = _state

    private val _messages: SnapshotStateList<ChatMessage> = listOf(
        ChatMessage(Sender.AI, "안녕하세요 😊\n오늘은 몇 월 며칠인가요?")
    ).toMutableStateList()
    val messages: List<ChatMessage> get() = _messages

    private var _recordingSeconds by mutableStateOf(0)
    val recordingSeconds: Int get() = _recordingSeconds

    private var _speakTrigger by mutableStateOf(0)
    val speakTrigger: Int get() = _speakTrigger

    private var timerJob: Job? = null

    // ── 초기화: 세션 시작 ─────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            runCatching { api.startSession(userId) }
                .onSuccess { resp -> sessionId = resp.body()?.data ?: -1L }
        }
    }

    // ── 녹음 ──────────────────────────────────────────────────────────────────

    fun startRecording() {
        if (_state !is VoiceChatState.Idle) return
        _state = VoiceChatState.Recording
        _recordingSeconds = 0
        wavRecorder.start()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds += 1
            }
        }
    }

    fun stopRecording() {
        if (_state !is VoiceChatState.Recording) return
        timerJob?.cancel()
        timerJob = null

        val file = wavRecorder.stop()
        _state = VoiceChatState.Processing

        viewModelScope.launch {
            // 세션·파일이 있으면 업로드 시도 (실패해도 계속 진행)
            if (file != null && sessionId > 0) {
                runCatching {
                    val body = file.asRequestBody("audio/wav".toMediaType())
                    val part = MultipartBody.Part.createFormData("file", file.name, body)
                    api.uploadVoice(userId, sessionId, part)
                }
            }
            delay(2000L) // TODO: AI 응답 구현 후 제거
            dispatchAiReply("AI 답변 구현 예정입니다.")
        }
    }

    // ── AI 응답 수신 (서버 미구현 — 나중에 연결) ──────────────────────────────

    /**
     * 서버에서 AI 응답 텍스트가 오면 이 함수를 호출한다.
     * 현재는 미사용 — 백엔드 응답 엔드포인트 구현 후 연결할 것.
     */
    fun dispatchAiReply(text: String) {
        _messages.add(ChatMessage(Sender.AI, text, isSpeaking = true))
        _state = VoiceChatState.Playing
        _speakTrigger++
    }

    // ── TTS 제어 ──────────────────────────────────────────────────────────────

    fun finishPlaying() {
        if (_state !is VoiceChatState.Playing) return
        val last = _messages.lastOrNull()
        if (last != null && last.isSpeaking) {
            _messages[_messages.lastIndex] = last.copy(isSpeaking = false)
        }
        _state = VoiceChatState.Idle
    }

    fun replayLastAi() {
        if (_messages.lastOrNull()?.from != Sender.AI) return
        val last = _messages.last()
        if (!last.isSpeaking) {
            _messages[_messages.lastIndex] = last.copy(isSpeaking = true)
        }
        _state = VoiceChatState.Playing
        _speakTrigger++
    }

    fun speakMessage(index: Int) {
        val msg = _messages.getOrNull(index) ?: return
        if (msg.from != Sender.AI || msg.isLoading) return
        _messages.replaceAll { it.copy(isSpeaking = false) }
        _messages[index] = msg.copy(isSpeaking = true)
        _state = VoiceChatState.Playing
        _speakTrigger++
    }

    override fun onCleared() {
        timerJob?.cancel()
        wavRecorder.stop()
        super.onCleared()
    }
}
