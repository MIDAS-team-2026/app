package com.midas26.mobileapp.ui.voicechat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.midas26.mobileapp.ui.tutorial.TutorialScreen
import com.midas26.mobileapp.ui.tutorial.TutorialViewModel
import com.midas26.mobileapp.ui.tutorial.VoiceChatTutorialStep
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
 *
 * stt_debug: 마이크 녹음 대신 텍스트 입력으로 대화한다 (TextInputRow).
 */
@Composable
fun VoiceChatScreen(
    tutorialViewModel: TutorialViewModel,
    onBack: () -> Unit,
    onDisconnected: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSessionEnded: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateAnalysis: () -> Unit = {},
    viewModel: VoiceChatViewModel = viewModel()
) {
    val state = viewModel.state
    val ttsManager = LocalTtsManager.current
    val tapToReplay = LocalTapToReplay.current
    val tutorialState by tutorialViewModel.state.collectAsState()

    /*
     * 현재 프로젝트의 Compose 버전에서는 localBoundingBoxOf를 사용할 수 없으므로
     * 최상위 화면의 boundsInRoot 값을 저장합니다.
     *
     * 각 강조 대상도 boundsInRoot로 측정한 뒤 루트 위치를 빼서
     * VoiceChatScreen 내부 오버레이 좌표로 변환합니다.
     */
    var tutorialRootBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    // TutorialOverlay에 전달할 실제 UI 위치입니다.
    var speechBubbleBounds by remember { mutableStateOf<Rect?>(null) }
    var inputAreaBounds by remember { mutableStateOf<Rect?>(null) }
    var endButtonBounds by remember { mutableStateOf<Rect?>(null) }

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
     * 3. 메시지를 보낸 적이 있다면 세션 종료
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
            .onGloballyPositioned { coordinates ->
                tutorialRootBounds = coordinates.boundsInRoot()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                        .onGloballyPositioned { coordinates ->
                            speechBubbleBounds = coordinates
                                .boundsInRoot()
                                .relativeTo(tutorialRootBounds)
                        }
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
                            .height(140.dp)
                            .onGloballyPositioned { coordinates ->
                                inputAreaBounds = coordinates
                                    .boundsInRoot()
                                    .relativeTo(tutorialRootBounds)
                            },
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
                            .onGloballyPositioned { coordinates ->
                                endButtonBounds = coordinates
                                    .boundsInRoot()
                                    .relativeTo(tutorialRootBounds)
                            }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

        }
    }

    if (
        tutorialState.isRunning &&
        tutorialState.currentScreen == TutorialScreen.VOICE_CHAT
    ) {
        val targetBounds = when (tutorialState.voiceChatStep) {
            VoiceChatTutorialStep.MESSAGE -> speechBubbleBounds
            VoiceChatTutorialStep.MICROPHONE -> inputAreaBounds
            VoiceChatTutorialStep.END_BUTTON -> endButtonBounds
            VoiceChatTutorialStep.COMPLETED -> null
        }

        /*
         * 강조 대상 위치가 측정되기 전에는 오버레이를 표시하지 않습니다.
         * targetBounds가 null인 상태에서 오버레이를 그리면 화면 전체가
         * 회색 스크림으로만 덮여 실제 대화 화면이 보이지 않을 수 있습니다.
         */
        if (
            targetBounds != null ||
            tutorialState.voiceChatStep == VoiceChatTutorialStep.COMPLETED
        ) {
            VoiceChatTutorialOverlay(
                step = tutorialState.voiceChatStep,
                targetBounds = targetBounds,
                currentNumber = tutorialState.currentNumber,
                totalNumber = tutorialState.totalNumber,
                onNext = {
                    when (tutorialState.voiceChatStep) {
                        VoiceChatTutorialStep.MESSAGE -> {
                            tutorialViewModel.moveVoiceChatStep(
                                step = VoiceChatTutorialStep.MICROPHONE,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        VoiceChatTutorialStep.MICROPHONE -> {
                            tutorialViewModel.moveVoiceChatStep(
                                step = VoiceChatTutorialStep.END_BUTTON,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        VoiceChatTutorialStep.END_BUTTON -> {
                            if (tutorialViewModel.isFullTutorial()) {
                                tutorialViewModel.moveToAnalysis()
                                onNavigateAnalysis()
                            } else {
                                tutorialViewModel.completeTutorial()
                            }
                        }

                        VoiceChatTutorialStep.COMPLETED -> {
                            tutorialViewModel.completeTutorial()
                        }
                    }
                },
                onSkip = {
                    tutorialViewModel.stopTutorial()
                }
            )
        }
    }
}

private fun Rect.relativeTo(rootBounds: Rect?): Rect? {
    val root = rootBounds ?: return null

    return Rect(
        left = left - root.left,
        top = top - root.top,
        right = right - root.left,
        bottom = bottom - root.top
    )
}

@Composable
private fun VoiceChatTutorialOverlay(
    step: VoiceChatTutorialStep,
    targetBounds: Rect?,
    currentNumber: Int,
    totalNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current

    /*
     * AppColor 색상이 @Composable getter일 수 있으므로
     * Canvas DrawScope 외부에서 먼저 읽어 둡니다.
     */
    val highlightColor = AppColor.greenPrimary

    val title = when (step) {
        VoiceChatTutorialStep.MESSAGE -> "또바기의 말 확인하기"
        VoiceChatTutorialStep.MICROPHONE -> "메시지로 대화하기"
        VoiceChatTutorialStep.END_BUTTON -> "대화 종료하기"
        VoiceChatTutorialStep.COMPLETED -> "음성 대화 사용법 완료"
    }

    val description = when (step) {
        VoiceChatTutorialStep.MESSAGE ->
            "또바기가 하는 말은 화면 위쪽 말풍선에서 확인할 수 있어요."

        VoiceChatTutorialStep.MICROPHONE ->
            "입력창에 편하게 메시지를 입력하고 전송 버튼을 눌러 주세요."

        VoiceChatTutorialStep.END_BUTTON ->
            "대화를 마치고 싶을 때는 대화종료 버튼을 눌러 주세요."

        VoiceChatTutorialStep.COMPLETED ->
            "음성 대화 화면의 주요 기능을 모두 살펴봤어요."
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {})
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val showPopupAtTop = targetBounds?.let { bounds ->
            bounds.center.y > screenHeightPx * 0.52f
        } ?: false

        val popupAlignment = if (showPopupAtTop) {
            Alignment.TopCenter
        } else {
            Alignment.BottomCenter
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            drawRect(
                color = Color.Black.copy(alpha = 0.40f)
            )

            targetBounds?.let { bounds ->
                val padding = 5.dp.toPx()
                val left = (bounds.left - padding).coerceAtLeast(0f)
                val top = (bounds.top - padding).coerceAtLeast(0f)
                val right = (bounds.right + padding).coerceAtMost(size.width)
                val bottom = (bounds.bottom + padding).coerceAtMost(size.height)

                val topLeft = Offset(left, top)
                val highlightSize = Size(
                    width = (right - left).coerceAtLeast(0f),
                    height = (bottom - top).coerceAtLeast(0f)
                )
                val cornerRadius = CornerRadius(
                    x = 20.dp.toPx(),
                    y = 20.dp.toPx()
                )

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = topLeft,
                    size = highlightSize,
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = highlightColor,
                    topLeft = topLeft,
                    size = highlightSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(popupAlignment)
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (showPopupAtTop) 24.dp else 20.dp,
                    bottom = if (showPopupAtTop) 20.dp else 28.dp
                ),
            shape = RoundedCornerShape(24.dp),
            color = BrandWhite,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    text = "$currentNumber / $totalNumber",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColor.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onSkip
                    ) {
                        Text(
                            text = "건너뛰기",
                            color = AppColor.textTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = onNext
                    ) {
                        Text(
                            text = if (
                                step == VoiceChatTutorialStep.END_BUTTON ||
                                step == VoiceChatTutorialStep.COMPLETED
                            ) {
                                if (totalNumber == 3) "완료" else "다음"
                            } else {
                                "다음"
                            },
                            color = AppColor.greenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
