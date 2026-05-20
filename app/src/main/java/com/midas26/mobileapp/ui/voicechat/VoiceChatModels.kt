package com.midas26.mobileapp.ui.voicechat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 채팅 메시지 보낸 사람. */
enum class Sender { AI, USER }

/**
 * 채팅 한 줄. UI 표시에 필요한 시각 상태(speaking, loading, faded) 포함.
 *
 * - [isLoading] : "AI 입력 중" 점 3개 표시
 * - [isSpeaking]: TTS 재생 중 — 말풍선에 음파 인디케이터
 * - [isFaded]  : 녹음/STT 확인 중일 때 이전 메시지 흐리게
 */
data class ChatMessage(
    val from: Sender,
    val text: String,
    val isLoading: Boolean = false,
    val isSpeaking: Boolean = false
)

/**
 * 음성 대화 화면의 상태 머신.
 *  - Idle      : 채팅 + 큰 마이크 버튼
 *  - Recording : 녹음 중 (펄스/타이머/음파/정지 버튼)
 *  - Reviewing : STT 결과 확인 (인용 카드 + 전송/다시 녹음)
 *  - Playing   : TTS 재생 중 (마지막 AI 메시지에 음파, 하단 "다시 듣기/답변하기")
 */
sealed interface VoiceChatState {
    data object Idle : VoiceChatState
    data object Recording : VoiceChatState
    data object Processing : VoiceChatState           // STT 처리 중
    data class Reviewing(val sttText: String) : VoiceChatState
    data object Waiting : VoiceChatState              // AI 응답 대기 중
    data object Playing : VoiceChatState
}

/**
 * 음성 대화 ViewModel — 더미 구현.
 *
 * 실제 백엔드/STT/TTS 없이 UI 흐름만 검증할 수 있도록 다음을 시뮬레이션합니다.
 *  - 녹음 시작 시 1초마다 타이머 증가
 *  - "녹음 중지" 시 미리 정해진 STT 문장으로 Reviewing 진입
 *  - "전송" 시 사용자 메시지 추가 → AI "입력 중" 점 표시 → 1.5초 후 더미 답변 → Playing
 *  - "답변하기" 시 다시 Idle (녹음 시작 가능 상태)
 */
class VoiceChatViewModel : ViewModel() {

    private var _state by mutableStateOf<VoiceChatState>(VoiceChatState.Idle)
    val state: VoiceChatState get() = _state

    private val _messages: SnapshotStateList<ChatMessage> = listOf(
        ChatMessage(Sender.AI,   "안녕하세요 %% 님 😊\n오늘은 몇 월 며칠인가요?"),
        ChatMessage(Sender.USER, "오늘은 5월 5일이에요."),
        ChatMessage(Sender.AI,   "정확해요! 어제는 주로\n무엇을 하셨나요? 😊")
    ).toMutableStateList()
    val messages: List<ChatMessage> get() = _messages

    private var _recordingSeconds by mutableStateOf(0)
    val recordingSeconds: Int get() = _recordingSeconds

    // TTS 재생 트리거 — 값이 바뀔 때마다 Screen이 TTS를 실행
    private var _speakTrigger by mutableStateOf(0)
    val speakTrigger: Int get() = _speakTrigger

    private var timerJob: Job? = null

    /** 미리 정해진 STT 결과 시퀀스 — 녹음할 때마다 순차적으로 사용. */
    private val cannedStt = listOf(
        "병원에 다녀왔어요.",
        "친구를 만나서 점심을 먹었어요.",
        "공원에서 산책했어요.",
        "집에서 텔레비전을 봤어요."
    )
    private val cannedAiReplies = listOf(
        "그러셨군요. 병원에서는\n어떤 진료를 받으셨어요?",
        "어떤 친구분과 만나셨나요? 😊\n즐거운 시간이셨겠어요.",
        "산책 좋으셨겠네요!\n어디 공원에 다녀오셨어요?",
        "어떤 프로그램을 보셨어요?\n재밌으셨나요?"
    )
    private var sttIndex = 0

    fun startRecording() {
        if (_state !is VoiceChatState.Idle) return
        _state = VoiceChatState.Recording
        _recordingSeconds = 0
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
        _state = VoiceChatState.Processing
        viewModelScope.launch {
            delay(1500)
            val stt = cannedStt[sttIndex.coerceAtMost(cannedStt.lastIndex)]
            _state = VoiceChatState.Reviewing(sttText = stt)
        }
    }

    fun retryRecording() {
        if (_state !is VoiceChatState.Reviewing) return
        _state = VoiceChatState.Recording
        _recordingSeconds = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds += 1
            }
        }
    }

    fun sendStt() {
        val current = _state as? VoiceChatState.Reviewing ?: return
        sendText(current.sttText)
    }

    fun sendText(text: String) {
        if (_state !is VoiceChatState.Reviewing) return
        _messages.add(ChatMessage(Sender.USER, text))
        val loadingMsg = ChatMessage(Sender.AI, "", isLoading = true)
        _messages.add(loadingMsg)
        _state = VoiceChatState.Waiting

        viewModelScope.launch {
            delay(1500)
            val idx = _messages.indexOf(loadingMsg)
            if (idx >= 0) _messages.removeAt(idx)
            val reply = cannedAiReplies.getOrNull(sttIndex) ?: cannedAiReplies.last()
            _messages.add(ChatMessage(Sender.AI, reply, isSpeaking = true))
            sttIndex += 1
            _state = VoiceChatState.Playing
            _speakTrigger++
        }
    }

    fun finishPlaying() {
        if (_state !is VoiceChatState.Playing) return
        // 음파 인디케이터 제거 후 idle 로
        val last = _messages.lastOrNull()
        if (last != null && last.isSpeaking) {
            _messages[_messages.lastIndex] = last.copy(isSpeaking = false)
        }
        _state = VoiceChatState.Idle
    }

    /** "다시 듣기" — 마지막 AI 메시지를 다시 재생. */
    fun replayLastAi() {
        if (_messages.lastOrNull()?.from != Sender.AI) return
        val last = _messages.last()
        if (!last.isSpeaking) {
            _messages[_messages.lastIndex] = last.copy(isSpeaking = true)
        }
        _state = VoiceChatState.Playing
        _speakTrigger++
    }

    /** 말풍선 탭 시 해당 AI 메시지 재생 (누르면 음성 재생하기). */
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
        super.onCleared()
    }
}
