package com.midas26.mobileapp.ui.voicechat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.LocalTapToReplay
import com.midas26.mobileapp.ui.theme.LocalTtsManager
import kotlinx.coroutines.delay

private const val END_CONVERSATION_MESSAGE = "수고하셨습니다!"

/**
 * 음성 대화 화면 — 캐릭터 중심 UI.
 *
 * 말풍선·캐릭터·버튼이 Box 절대 배치로 고정되어
 * 상태가 바뀌어도 위치가 움직이지 않는다.
 *
 * TopCenter    : AiSpeechBubble
 * Center       : CharacterImage
 * BottomCenter : 상태별 버튼 / 음파 / 대화종료 버튼
 */
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    onDisconnected: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSessionEnded: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    viewModel: VoiceChatViewModel = viewModel()
) {
    val state = viewModel.state
    val ttsManager = LocalTtsManager.current
    val tapToReplay = LocalTapToReplay.current

    // 대화 종료 진행 여부
    var isEnding by remember {
        mutableStateOf(false)
    }

    // 다시 말하기 비활성화 안내 표시 여부
    var showReplayHint by remember {
        mutableStateOf(false)
    }

    // 텍스트 입력창 내용
    var inputText by remember {
        mutableStateOf("")
    }

    /*
     * 대화 종료 버튼 클릭 후 2초 뒤 홈 화면으로 이동한다.
     *
     * 실제 화면 이동 코드는 AppNavGraph.kt에서
     * onNavigateHome에 전달한다.
     */
    LaunchedEffect(isEnding) {
        if (isEnding) {
            delay(2000L)
            onNavigateHome()
        }
    }

    // 다시 말하기 안내 카드는 3초 후 자동으로 숨긴다.
    LaunchedEffect(showReplayHint) {
        if (showReplayHint) {
            delay(3000L)
            showReplayHint = false
        }
    }

    /*
     * TTS 재생
     *
     * speakTrigger가 변경되면 현재 문장을 재생한다.
     * speakTrigger == 0은 초기값이므로 무시한다.
     *
     * 대화 종료 중에는 기존 AI 문장을 새로 재생하지 않는다.
     */
    LaunchedEffect(viewModel.speakTrigger, isEnding) {
        if (isEnding) return@LaunchedEffect
        if (viewModel.speakTrigger == 0) return@LaunchedEffect

        val text = viewModel.displayedText

        if (text.isBlank()) return@LaunchedEffect

        ttsManager?.speak(text) {
            viewModel.advanceSentence()
        }
    }

    /*
     * 화면에서 벗어날 때:
     * 1. TTS 중단
     * 2. 재생 상태 초기화
     * 3. 업로드된 음성이 있다면 세션 종료
     */
    DisposableEffect(Unit) {
        onDispose {
            ttsManager?.stop()
            viewModel.finishPlaying()

            if (viewModel.hasSentMessage()) {
                viewModel.endSession()
                onSessionEnded()
            }
        }
    }

    /*
     * 종료 버튼을 누른 경우 기존 AI 문장 대신
     * "수고하셨습니다!"를 말풍선에 표시한다.
     */
    val aiText = if (isEnding) {
        END_CONVERSATION_MESSAGE
    } else {
        viewModel.displayedText
    }

    val isPlaying =
        state is VoiceChatState.Playing && !isEnding

    /*
     * 말풍선 또는 캐릭터 탭 처리
     *
     * 종료 중에는 다시 말하기를 사용할 수 없다.
     */
    val replayTap: (() -> Unit)? = when {
        isEnding -> null

        state !is VoiceChatState.Idle &&
                state !is VoiceChatState.Playing -> null

        tapToReplay -> viewModel::replayLastAi

        else -> {
            {
                showReplayHint = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        VoiceChatTopBar(
            onBack = {
                if (!isEnding) {
                    onBack()
                }
            }
        )

        // ── 메인 영역 ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // ① AI 말풍선
            AiSpeechBubble(
                text = aiText,
                highlighted = isPlaying,
                onClick = replayTap,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp
                    )
            )

            // ② 캐릭터 이미지
            CharacterImage(
                state = state,
                onClick = replayTap,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-10).dp)
            )

            // ③ 다시 말하기 비활성화 안내 카드
            val hintAlpha by animateFloatAsState(
                targetValue = if (showReplayHint && !isEnding) {
                    1f
                } else {
                    0f
                },
                animationSpec = tween(durationMillis = 300),
                label = "hintAlpha"
            )

            if (hintAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = 252.dp,
                            start = 24.dp,
                            end = 24.dp
                        )
                        .graphicsLayer {
                            alpha = hintAlpha
                        }
                ) {
                    ReplayDisabledHintCard(
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }

            // ④ 하단 컨트롤
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 텍스트 입력창 / 전송 중 웨이브폼
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (
                        state is VoiceChatState.Processing &&
                        !isEnding
                    ) {
                        Waveform(
                            barColor = AppColor.greenPrimary,
                            barCount = 13,
                            maxBarHeight = 40.dp,
                            barWidth = 12.dp,
                            modifier = Modifier.height(56.dp),
                            animated = true
                        )
                    } else {
                        TextInputRow(
                            value = inputText,
                            onValueChange = { inputText = it },
                            onSend = {
                                if (isEnding) {
                                    return@TextInputRow
                                }

                                if (state is VoiceChatState.Playing) {
                                    ttsManager?.stop()
                                    viewModel.finishPlaying()
                                }

                                val text = inputText
                                inputText = ""
                                viewModel.sendText(text)
                            },
                            enabled = state is VoiceChatState.Idle && !isEnding
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                EndConversationButton(
                    isEnding = isEnding,
                    onClick = {
                        if (isEnding) {
                            return@EndConversationButton
                        }

                        /*
                         * AI 음성이 재생 중이라면 기존 TTS와
                         * Playing 상태를 종료한다.
                         */
                        if (
                            state is VoiceChatState.Playing
                        ) {
                            ttsManager?.stop()
                            viewModel.finishPlaying()
                        } else {
                            ttsManager?.stop()
                        }

                        // 다시 말하기 안내 카드가 떠 있다면 숨긴다.
                        showReplayHint = false

                        /*
                         * 먼저 종료 상태를 true로 변경하여
                         * 말풍선 문구를 바꾼다.
                         */
                        isEnding = true

                        /*
                         * 캐릭터가 종료 인사를 말하도록 TTS를 재생한다.
                         * 화면은 2초 후 자동으로 홈으로 이동한다.
                         */
                        ttsManager?.speak(
                            END_CONVERSATION_MESSAGE
                        ) {
                            // 종료 TTS 완료 후 별도 문장 이동 없음
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 대화종료 버튼
 */
@Composable
private fun EndConversationButton(
    isEnding: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isEnding) {
        Color(0xFFE0E0E0)
    } else {
        Color(0xFFD6EED8)
    }

    val borderColor = if (isEnding) {
        Color(0xFFBDBDBD)
    } else {
        Color(0xFF43A047)
    }

    val textColor = if (isEnding) {
        Color(0xFF8A8A8A)
    } else {
        Color(0xFF1F2937)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                enabled = !isEnding,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isEnding) {
                "대화 종료 중..."
            } else {
                "대화종료"
            },
            style = MaterialTheme.typography.titleMedium,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun ReplayDisabledHintCard(
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x991C1C1E))
            .clickable(onClick = onNavigateToSettings)
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
    ) {
        Column {
            Text(
                text = "다시 말하기 기능이 꺼져 있어요!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandWhite
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF64B5F6),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("설정화면으로 이동하기 →")
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}