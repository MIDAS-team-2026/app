package com.midas26.mobileapp.ui.voicechat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.LocalTtsManager

/**
 * 음성 대화 화면 — 캐릭터 중심 UI.
 *
 * 말풍선·캐릭터·버튼이 Box 절대 배치로 고정되어
 * 상태가 바뀌어도 위치가 전혀 움직이지 않는다.
 *
 *  TopCenter  : AiSpeechBubble
 *  Center     : CharacterImage (항상 동일한 위치)
 *  BottomCenter: 상태별 버튼 / 음파
 */
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    onDisconnected: () -> Unit = {},
    viewModel: VoiceChatViewModel = viewModel()
) {
    val state    = viewModel.state
    val messages = viewModel.messages
    val ttsManager = LocalTtsManager.current

    // TTS 재생 — speakTrigger 변경 시 마지막 AI 메시지 읽기
    LaunchedEffect(viewModel.speakTrigger) {
        val speaking = messages.lastOrNull { it.isSpeaking } ?: return@LaunchedEffect
        ttsManager?.speak(speaking.text) { viewModel.finishPlaying() }
    }

    // 녹음 시작 시 TTS 즉시 중단
    LaunchedEffect(state) {
        if (state is VoiceChatState.Recording) ttsManager?.stop()
    }

    // 화면 벗어날 때 TTS 중단 및 재생 상태 초기화
    DisposableEffect(Unit) {
        onDispose {
            ttsManager?.stop()
            viewModel.finishPlaying()
        }
    }

    val aiText    = messages.lastOrNull { it.from == Sender.AI && !it.isLoading }?.text ?: ""
    val isPlaying = state is VoiceChatState.Playing

    // 말풍선·캐릭터 탭 → AI 다시 말하기 (Idle / Playing 상태에서만)
    val replayTap: (() -> Unit)? = when (state) {
        is VoiceChatState.Idle, is VoiceChatState.Playing -> viewModel::replayLastAi
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        VoiceChatTopBar(onBack = onBack)

        // ── 메인 영역: Box 절대 배치로 위치 완전 고정 ─────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // ① 말풍선 — 항상 상단 고정
            AiSpeechBubble(
                text        = aiText,
                highlighted = isPlaying,
                onClick     = replayTap,
                modifier    = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )

            // ② 캐릭터 — 항상 중앙 고정
            CharacterImage(
                state    = state,
                onClick  = replayTap,
                modifier = Modifier.align(Alignment.Center)
            )

            // ③ 하단 컨트롤 — 항상 하단 고정
            // 콘텐츠 영역을 RecordingPulse(220dp)와 동일하게 고정해
            // Idle/Recording 사이에서 버튼 중심 Y가 절대 바뀌지 않도록 한다.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is VoiceChatState.Processing ->
                            Waveform(
                                barColor     = Green400,
                                barCount     = 13,
                                maxBarHeight = 40.dp,
                                barWidth     = 12.dp,
                                modifier     = Modifier.height(56.dp),
                                animated     = true
                            )

                        is VoiceChatState.Recording ->
                            Box(contentAlignment = Alignment.Center) {
                                RecordingPulse()
                                BigActionButton(
                                    mode    = BigActionMode.Stop,
                                    onClick = viewModel::stopRecording
                                )
                            }

                        else -> // Idle, Playing
                            BigActionButton(
                                mode    = BigActionMode.Mic,
                                onClick = {
                                    if (state is VoiceChatState.Playing) {
                                        ttsManager?.stop()
                                        viewModel.finishPlaying()
                                    }
                                    viewModel.startRecording()
                                }
                            )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
