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
import androidx.compose.ui.platform.LocalView
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

    var tutorialRootBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var speechBubbleBounds by remember { mutableStateOf<Rect?>(null) }
    var microphoneBounds by remember { mutableStateOf<Rect?>(null) }
    var endButtonBounds by remember { mutableStateOf<Rect?>(null) }

    var isEnding by remember {
        mutableStateOf(false)
    }

    var showReplayHint by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isEnding) {
        if (isEnding) {
            delay(2000L)
            onNavigateHome()
        }
    }

    LaunchedEffect(showReplayHint) {
        if (showReplayHint) {
            delay(3000L)
            showReplayHint = false
        }
    }

    LaunchedEffect(viewModel.speakTrigger, isEnding) {
        if (isEnding) return@LaunchedEffect
        if (viewModel.speakTrigger == 0) return@LaunchedEffect

        val text = viewModel.displayedText

        if (text.isBlank()) return@LaunchedEffect

        if (ttsManager != null) {
            ttsManager.speak(text) {
                viewModel.advanceSentence()
            }
        } else {
            // 음성 안내를 꺼 둔 경우에는 읽기가 끝났다는 신호가 오지 않는다.
            // 문장을 읽을 시간을 준 뒤 다음 문장으로 넘긴다. (예전에는 첫 문장에서 멈췄다.)
            delay((text.length * 120L).coerceIn(1_500L, 6_000L))
            viewModel.advanceSentence()
        }
    }

    // 대화 중에는 화면이 꺼지지 않게 한다. (답변을 말하거나 기다리는 동안 화면을 건드리지 않기 때문)
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 녹음이 시작되면 기존 TTS를 즉시 중단
    LaunchedEffect(state) {
        if (state is VoiceChatState.Recording) {
            ttsManager?.stop()
        }
    }

    // 화면 이탈 시 TTS와 세션을 정리

    DisposableEffect(Unit) {
        onDispose {
            ttsManager?.stop()
            viewModel.finishPlaying()

            if (viewModel.hasUploadedVoice()) {
                viewModel.endSession()
                onSessionEnded()
            }
        }
    }

    val aiText = if (isEnding) {
        END_CONVERSATION_MESSAGE
    } else {
        viewModel.displayedText
    }

    val isPlaying =
        state is VoiceChatState.Playing && !isEnding

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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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

                CharacterImage(
                    state = state,
                    onClick = replayTap,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-10).dp)
                )

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

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
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

                            Box(
                                modifier = Modifier
                                    .offset(y = (-18).dp)
                                    .onGloballyPositioned { coordinates ->
                                        microphoneBounds = coordinates
                                            .boundsInRoot()
                                            .relativeTo(tutorialRootBounds)
                                    }
                            ) {
                                BigActionButton(
                                    mode = if (
                                        state is VoiceChatState.Recording
                                    ) {
                                        BigActionMode.Stop
                                    } else {
                                        BigActionMode.Mic
                                    },
                                    onClick = {
                                        if (isEnding) {
                                            return@BigActionButton
                                        }

                                        if (
                                            state is VoiceChatState.Recording
                                        ) {
                                            viewModel.stopRecording()
                                        } else {
                                            if (
                                                state is VoiceChatState.Playing
                                            ) {
                                                ttsManager?.stop()
                                                viewModel.finishPlaying()
                                            }

                                            viewModel.startRecording()
                                        }
                                    }
                                )
                            }
                        }

                        // 대화종료 버튼
                        EndConversationButton(
                            isEnding = isEnding,
                            onClick = {
                                if (isEnding) {
                                    return@EndConversationButton
                                }

                                if (
                                    state is VoiceChatState.Recording
                                ) {
                                    viewModel.stopRecording()
                                }

                                if (
                                    state is VoiceChatState.Playing
                                ) {
                                    ttsManager?.stop()
                                    viewModel.finishPlaying()
                                } else {
                                    ttsManager?.stop()
                                }

                                showReplayHint = false

                                isEnding = true

                                ttsManager?.speak(
                                    END_CONVERSATION_MESSAGE
                                ) {
                                    // 종료 TTS 완료 후 별도 문장 이동 없음
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    bottom = 0.dp
                                )
                                .offset(y = 12.dp)
                                .onGloballyPositioned { coordinates ->
                                    endButtonBounds = coordinates
                                        .boundsInRoot()
                                        .relativeTo(tutorialRootBounds)
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

        }
    }

    if (
        tutorialState.isRunning &&
        tutorialState.currentScreen == TutorialScreen.VOICE_CHAT &&
        tutorialState.voiceChatStep != VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB
    ) {
        val targetBounds = when (tutorialState.voiceChatStep) {
            VoiceChatTutorialStep.MESSAGE -> speechBubbleBounds
            VoiceChatTutorialStep.MICROPHONE -> microphoneBounds
            VoiceChatTutorialStep.END_BUTTON -> endButtonBounds
            VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB -> null
            VoiceChatTutorialStep.COMPLETED -> null
        }

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

                                tutorialViewModel.showAnalysisTabGuide(
                                    number = tutorialState.currentNumber + 1
                                )
                            } else {
                                tutorialViewModel.completeTutorial()
                            }
                        }

                        VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB -> {

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

    val highlightColor = AppColor.greenPrimary

    val title = when (step) {
        VoiceChatTutorialStep.MESSAGE -> "또바기의 말 확인하기"
        VoiceChatTutorialStep.MICROPHONE -> "마이크로 대화하기"
        VoiceChatTutorialStep.END_BUTTON -> "대화 종료하기"
        VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB -> "분석 결과 확인하기"
        VoiceChatTutorialStep.COMPLETED -> "음성 대화 사용법 완료"
    }

    val description = when (step) {
        VoiceChatTutorialStep.MESSAGE ->
            "또바기가 하는 말은 화면 위쪽 말풍선에서 확인할 수 있어요."

        VoiceChatTutorialStep.MICROPHONE ->
            "마이크 버튼을 누르고 편하게 말씀해 주세요. 녹음 중에는 다시 눌러 대화를 멈출 수 있어요."

        VoiceChatTutorialStep.END_BUTTON ->
            "대화를 마치고 싶을 때는 대화종료 버튼을 눌러 주세요."

        VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB ->
            "하단 분석 탭에서 오늘의 인지 점수와 주간 변화를 확인할 수 있어요."

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
                // 말풍선 단계는 하이라이트의 좌우 여백을 넓힌다.
                val horizontalPadding = if (step == VoiceChatTutorialStep.MESSAGE) 12.dp.toPx() else 5.dp.toPx()
                val verticalPadding = 5.dp.toPx()
                val left = (bounds.left - horizontalPadding).coerceAtLeast(0f)
                val top = (bounds.top - verticalPadding).coerceAtLeast(0f)
                val right = (bounds.right + horizontalPadding).coerceAtMost(size.width)
                val bottom = (bounds.bottom + verticalPadding).coerceAtMost(size.height)

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
                "대화 종료 중"
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
