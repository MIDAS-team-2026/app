package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled
import com.midas26.mobileapp.ui.tutorial.AnalysisTutorialStep
import com.midas26.mobileapp.ui.tutorial.TutorialScreen
import com.midas26.mobileapp.ui.tutorial.TutorialViewModel
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianAnalysisTutorialStep
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialScreen
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialViewModel
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay
import kotlin.math.abs


@Composable
fun AnalysisResultScreen(
    tutorialViewModel: TutorialViewModel,
    onBack: () -> Unit,
    onNavigateSettings: () -> Unit = {},
    onNavigateTextScoreDetail: () -> Unit = {},
    viewModel: AnalysisViewModel = viewModel(),
    guardianTutorialViewModel: GuardianTutorialViewModel? = null
) {
    val context = LocalContext.current
    val isGuardian = PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN

    UserAnalysisResultContent(
        tutorialViewModel = tutorialViewModel,
        onBack = onBack,
        onNavigateSettings = onNavigateSettings,
        onNavigateTextScoreDetail = onNavigateTextScoreDetail,
        viewModel = viewModel,
        isGuardian = isGuardian,
        guardianTutorialViewModel = guardianTutorialViewModel
    )
}

@Composable
private fun UserAnalysisResultContent(
    tutorialViewModel: TutorialViewModel,
    onBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateTextScoreDetail: () -> Unit = {},
    viewModel: AnalysisViewModel,
    isGuardian: Boolean = false,
    guardianTutorialViewModel: GuardianTutorialViewModel? = null
) {
    val fontScale = LocalFontSizeScale.current.scale
    val tutorialState by tutorialViewModel.state.collectAsState()

    val guardianTutorialState =
        if (guardianTutorialViewModel != null) {
            guardianTutorialViewModel.state.collectAsState().value
        } else {
            null
        }

    /*
     * boundsInRoot()로 최상위 화면과 각 강조 대상의 위치를 측정한 뒤,
     * 루트 위치를 빼서 오버레이 내부 좌표로 변환합니다.
     */
    var tutorialRootBounds by remember { mutableStateOf<Rect?>(null) }

    // TutorialOverlay에 전달할 실제 UI 위치입니다.
    var mainScoreBounds by remember { mutableStateOf<Rect?>(null) }
    var weeklyGraphBounds by remember { mutableStateOf<Rect?>(null) }
    var detailScoresBounds by remember { mutableStateOf<Rect?>(null) }

    var minTimeElapsed by remember { mutableStateOf(!viewModel.showLoadingScreen) }

    LaunchedEffect(viewModel.showLoadingScreen) {
        if (viewModel.showLoadingScreen) {
            minTimeElapsed = false
            delay(2000)
            minTimeElapsed = true
        }
    }

    val showLoading = viewModel.showLoadingScreen && (viewModel.isLoading || !minTimeElapsed)

    LaunchedEffect(showLoading) {
        if (!showLoading && viewModel.showLoadingScreen) {
            viewModel.dismissLoadingScreen()
        }
    }

    Crossfade(
        targetState = showLoading,
        animationSpec = tween(durationMillis = 500),
        label = "analysis_crossfade"
    ) { loading ->
        if (loading) {
            AnalysisLoadingScreen(isLoading = viewModel.isLoading, onFinished = {}, isGuardian = isGuardian)
            return@Crossfade
        }

        val isDragging = viewModel.isDraggingGraph

        val scrimAlpha by animateFloatAsState(
            targetValue = if (isDragging) 0.52f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "scrim"
        )

        val dateLabelScale by animateFloatAsState(
            targetValue = if (isDragging) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "date_scale"
        )

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
                val isToday = viewModel.isViewingToday && viewModel.hasTodayData

                val animSpec = tween<Color>(durationMillis = 400)

                val topColor by animateColorAsState(
                    targetValue = if (isToday) (if (isGuardian) AppColor.guardianDark else AppColor.accentDark) else AppColor.textSecondary,
                    animationSpec = animSpec,
                    label = "top_color"
                )

                val botColor by animateColorAsState(
                    targetValue = if (isToday) (if (isGuardian) AppColor.guardianPrimary else AppColor.greenPrimary) else AppColor.textTertiary,
                    animationSpec = animSpec,
                    label = "bottom_color"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(topColor, botColor)
                            )
                        )
                ) {
                    Column(modifier = Modifier.wrapContentHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "뒤로가기",
                                    tint = BrandWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = "분석 결과",
                                style = MaterialTheme.typography.titleLarge,
                                color = BrandWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            )

                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                        ) {
                            Text(
                                text = viewModel.displayDateLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Transparent
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    mainScoreBounds = coordinates
                                        .boundsInRoot()
                                        .relativeTo(tutorialRootBounds)
                                },
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = viewModel.displayScore.toString(),
                                    fontSize = (56 * fontScale).sp,
                                    color = BrandWhite,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.size(4.dp))

                                Text(
                                    text = "점",
                                    fontSize = (18 * fontScale).sp,
                                    color = BrandWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Spacer(modifier = Modifier.size(12.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = BrandWhite,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                ) {
                                    Text(
                                        text = viewModel.displayRiskLevel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isToday) (if (isGuardian) AppColor.guardianDark else AppColor.accentDark) else AppColor.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )

                    Text(
                        text = viewModel.displayDateLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrandWhite.copy(alpha = 0.92f),
                        fontWeight = if (isDragging) FontWeight.ExtraBold else FontWeight.Normal,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = (28 + 16 + 56 * fontScale).dp)
                            .scale(dateLabelScale)
                    )
                }

                var graphCardHeightPx by remember { mutableStateOf(0) }
                val density = LocalDensity.current
                val graphCardHeightDp = with(density) { graphCardHeightPx.toDp() }

                Box(modifier = Modifier.fillMaxSize()) {
                    val items = viewModel.todayItems

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp + graphCardHeightDp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(2f))

                        /*
                         * 세부 분석 카드 3개만 감싸는 영역에 좌표 측정을 적용합니다.
                         * 종합 위험도는 상단 대표 점수와 중복되므로 하단 카드에서는 제외합니다.
                         */
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    detailScoresBounds = coordinates
                                        .boundsInRoot()
                                        .relativeTo(tutorialRootBounds)
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ItemCard(item = items[1], modifier = Modifier.weight(1f))
                                ItemCard(item = items[2], modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextScoreCard(
                                item = items[3],
                                status = viewModel.textScoreStatus,
                                onClick = onNavigateTextScoreDetail,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.weight(2f))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp)
                            .onGloballyPositioned { coordinates ->
                                graphCardHeightPx = coordinates.size.height
                                weeklyGraphBounds = coordinates
                                    .boundsInRoot()
                                    .relativeTo(tutorialRootBounds)
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = BrandWhite,
                        border = BorderStroke(1.5.dp, AppColor.divider)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "이번 주 추이",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColor.textPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "최근 7일 · 길게 눌러 날짜 탐색",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.textTertiary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            WeeklyLineChart(
                                points = viewModel.graphPoints,
                                highlightIndex = viewModel.graphHighlightIndex,
                                isGuardian = isGuardian,
                                onPointTapped = { date -> viewModel.onGraphPointTapped(date) },
                                onDragStart = { viewModel.onGraphDragStart() },
                                onDragMove = { date -> viewModel.onGraphDragMove(date) },
                                onDragEnd = { date -> viewModel.onGraphDragEnd(date) },
                                onDragCancel = { viewModel.onGraphDragCancel() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    }
                }
            }

            if (
                !isGuardian &&
                tutorialState.isRunning &&
                tutorialState.currentScreen == TutorialScreen.ANALYSIS &&
                tutorialState.analysisStep != AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB
            ) {
                val targetBounds = when (tutorialState.analysisStep) {
                    AnalysisTutorialStep.MAIN_SCORE -> mainScoreBounds
                    AnalysisTutorialStep.WEEKLY_GRAPH -> weeklyGraphBounds
                    AnalysisTutorialStep.DETAIL_SCORES -> detailScoresBounds
                    AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB -> null
                    AnalysisTutorialStep.COMPLETED -> null
                }

                AnalysisTutorialOverlay(
                    step = tutorialState.analysisStep,
                    targetBounds = targetBounds,
                    currentNumber = tutorialState.currentNumber,
                    totalNumber = tutorialState.totalNumber,
                    onNext = {
                        when (tutorialState.analysisStep) {
                            AnalysisTutorialStep.MAIN_SCORE -> {
                                tutorialViewModel.moveAnalysisStep(
                                    step = AnalysisTutorialStep.WEEKLY_GRAPH,
                                    number = tutorialState.currentNumber + 1
                                )
                            }

                            AnalysisTutorialStep.WEEKLY_GRAPH -> {
                                tutorialViewModel.moveAnalysisStep(
                                    step = AnalysisTutorialStep.DETAIL_SCORES,
                                    number = tutorialState.currentNumber + 1
                                )
                            }

                            AnalysisTutorialStep.DETAIL_SCORES -> {
                                if (tutorialViewModel.isFullTutorial()) {
                                    /*
                                     * 분석 결과 설명이 끝나면 AppNavGraph에서
                                     * 하단 설정 탭을 안내합니다.
                                     */
                                    tutorialViewModel.showSettingsTabGuide(
                                        number = tutorialState.currentNumber + 1
                                    )
                                } else {
                                    tutorialViewModel.completeTutorial()
                                }
                            }

                            AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB -> {
                                /*
                                 * 하단 탭 오버레이와 실제 이동은 AppNavGraph에서 처리합니다.
                                 */
                            }

                            AnalysisTutorialStep.COMPLETED -> {
                                tutorialViewModel.completeTutorial()
                            }
                        }
                    },
                    onSkip = {
                        tutorialViewModel.stopTutorial()
                    }
                )
            }

            val guardianState = guardianTutorialState

            if (
                isGuardian &&
                guardianTutorialViewModel != null &&
                guardianState?.isRunning == true &&
                guardianState.currentScreen ==
                GuardianTutorialScreen.ANALYSIS_RESULT &&
                guardianState.analysisStep !=
                GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB &&
                guardianState.analysisStep !=
                GuardianAnalysisTutorialStep.COMPLETED
            ) {
                val mappedStep = when (
                    guardianState.analysisStep
                ) {
                    GuardianAnalysisTutorialStep.MAIN_SCORE ->
                        AnalysisTutorialStep.MAIN_SCORE

                    GuardianAnalysisTutorialStep.WEEKLY_GRAPH ->
                        AnalysisTutorialStep.WEEKLY_GRAPH

                    GuardianAnalysisTutorialStep.DETAIL_SCORES ->
                        AnalysisTutorialStep.DETAIL_SCORES

                    GuardianAnalysisTutorialStep.USER_SELECT,
                    GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB,
                    GuardianAnalysisTutorialStep.COMPLETED ->
                        null
                }

                val guardianTargetBounds = when (
                    guardianState.analysisStep
                ) {
                    GuardianAnalysisTutorialStep.MAIN_SCORE ->
                        mainScoreBounds

                    GuardianAnalysisTutorialStep.WEEKLY_GRAPH ->
                        weeklyGraphBounds

                    GuardianAnalysisTutorialStep.DETAIL_SCORES ->
                        detailScoresBounds

                    GuardianAnalysisTutorialStep.USER_SELECT,
                    GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB,
                    GuardianAnalysisTutorialStep.COMPLETED ->
                        null
                }

                if (
                    mappedStep != null &&
                    guardianTargetBounds != null
                ) {
                    AnalysisTutorialOverlay(
                        step = mappedStep,
                        targetBounds = guardianTargetBounds,
                        currentNumber =
                            guardianState.currentNumber,
                        totalNumber =
                            guardianState.totalNumber,
                        onNext = {
                            when (
                                guardianState.analysisStep
                            ) {
                                GuardianAnalysisTutorialStep.MAIN_SCORE -> {
                                    guardianTutorialViewModel
                                        .moveAnalysisStep(
                                            step =
                                                GuardianAnalysisTutorialStep
                                                    .WEEKLY_GRAPH,
                                            number =
                                                guardianState.currentNumber + 1
                                        )
                                }

                                GuardianAnalysisTutorialStep.WEEKLY_GRAPH -> {
                                    guardianTutorialViewModel
                                        .moveAnalysisStep(
                                            step =
                                                GuardianAnalysisTutorialStep
                                                    .DETAIL_SCORES,
                                            number =
                                                guardianState.currentNumber + 1
                                        )
                                }

                                GuardianAnalysisTutorialStep.DETAIL_SCORES -> {
                                    if (
                                        guardianTutorialViewModel
                                            .isFullTutorial()
                                    ) {
                                        guardianTutorialViewModel
                                            .showLocationTabGuide(
                                                number =
                                                    guardianState
                                                        .currentNumber + 1
                                            )
                                    } else {
                                        guardianTutorialViewModel
                                            .completeTutorial()
                                    }
                                }

                                GuardianAnalysisTutorialStep.USER_SELECT,
                                GuardianAnalysisTutorialStep
                                    .MOVE_TO_LOCATION_TAB ->
                                    Unit

                                GuardianAnalysisTutorialStep.COMPLETED ->
                                    guardianTutorialViewModel
                                        .completeTutorial()
                            }
                        },
                        onSkip = {
                            guardianTutorialViewModel
                                .stopTutorial()
                        },
                        tutorialColor = Color(0xFFC85E48)
                    )
                }
            }

        }
    }

    /*
     * UserAnalysisResultContent 종료
     *
     * 위의 세 중괄호는 각각:
     * 1. 최상위 Box
     * 2. Crossfade content lambda
     * 3. UserAnalysisResultContent 함수
     * 를 닫습니다.
     */
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
private fun AnalysisTutorialOverlay(
    step: AnalysisTutorialStep,
    targetBounds: Rect?,
    currentNumber: Int,
    totalNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    tutorialColor: Color = AppColor.greenPrimary
) {
    val density = LocalDensity.current
    val highlightColor = tutorialColor

    val title = when (step) {
        AnalysisTutorialStep.MAIN_SCORE -> "오늘의 인지 점수"
        AnalysisTutorialStep.WEEKLY_GRAPH -> "이번 주 점수 추이"
        AnalysisTutorialStep.DETAIL_SCORES -> "세부 분석 결과"
        AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB -> "앱 설정 살펴보기"
        AnalysisTutorialStep.COMPLETED -> "분석 결과 사용법 완료"
    }

    val description = when (step) {
        AnalysisTutorialStep.MAIN_SCORE ->
            "오늘 진행한 음성 대화를 바탕으로 계산된 인지 점수와 위험 수준을 확인할 수 있어요.\n\n분석 첫 주에는 결과가 정확하지 않을 수 있습니다!"

        AnalysisTutorialStep.WEEKLY_GRAPH ->
            "최근 7일 동안 점수가 어떻게 변했는지 그래프로 확인할 수 있어요. 그래프를 길게 누르면 날짜별 결과를 볼 수 있어요."

        AnalysisTutorialStep.DETAIL_SCORES ->
            "기억력과 어휘력 등 세부 항목별 분석 결과를 확인할 수 있어요."

        AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB ->
            "하단 설정 탭에서 알림, 위치 공유와 접근성 기능을 변경할 수 있어요."

        AnalysisTutorialStep.COMPLETED ->
            "분석 결과 화면의 주요 기능을 모두 살펴봤어요."
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {})
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val showPopupAtTop = targetBounds?.center?.y?.let {
            it > screenHeightPx * 0.52f
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
            drawRect(Color.Black.copy(alpha = 0.58f))

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
                val cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())

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
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "$currentNumber / $totalNumber",
                    style = MaterialTheme.typography.labelLarge,
                    color = tutorialColor,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text = "건너뛰기",
                            color = AppColor.textTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(onClick = onNext) {
                        Text(
                            text = if (
                                step == AnalysisTutorialStep.DETAIL_SCORES ||
                                step == AnalysisTutorialStep.COMPLETED
                            ) {
                                if (totalNumber == 3) "완료" else "다음"
                            } else {
                                "다음"
                            },
                            color = tutorialColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyLineChart(
    points: List<DailyScore>,
    highlightIndex: Int = points.lastIndex,
    isGuardian: Boolean = false,
    onPointTapped: (String?) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragMove: (String?) -> Unit = {},
    onDragEnd: (String?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current

    val hasData = points.map { it.score > 0 }

    val filledScores = run {
        val result = points.map { it.score }.toMutableList()

        for (i in result.indices) {
            if (result[i] == 0) {
                result[i] = if (i > 0) {
                    result[i - 1]
                } else {
                    result.firstOrNull { it > 0 } ?: 0
                }
            }
        }

        result
    }

    val animatedHighlight by animateFloatAsState(
        targetValue = if (highlightIndex >= 0) highlightIndex.toFloat() else -999f,
        animationSpec = tween(durationMillis = 300),
        label = "highlight"
    )

    var computedXs = remember { listOf<Float>() }
    var lastDragIdx = remember { -1 }

    // Canvas는 @Composable 컨텍스트가 아니므로 색상 미리 캡처
    val colorPrimary       = if (isGuardian) AppColor.guardianPrimary else AppColor.greenPrimary
    val colorDark          = if (isGuardian) AppColor.guardianDark    else AppColor.accentDark
    val colorSecondary     = if (isGuardian) AppColor.guardianPrimary else AppColor.greenSecondary
    val colorTextTertiary  = AppColor.textTertiary
    // 기존 변수명 유지 (Canvas 블록에서 참조)
    val colorGreenPrimary   = colorPrimary
    val colorAccentDark     = colorDark
    val colorGreenSecondary = colorSecondary

    fun nearestIdx(offsetX: Float): Int {
        if (computedXs.isEmpty()) return -1

        return computedXs.indices.minByOrNull {
            abs(computedXs[it] - offsetX)
        }?.takeIf {
            hasData[it]
        } ?: -1
    }

    val density = LocalDensity.current
    // 포인트 X 계산에 쓰이는 좌우 여백 — Canvas 와 요일 Row 에서 동일하게 사용
    val hPadDp = 8.dp
    val hPadPx = with(density) { hPadDp.toPx() }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = hPadDp, vertical = 8.dp)
                .pointerInput(points) {
                    val longPressMs = viewConfiguration.longPressTimeoutMillis

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        lastDragIdx = -1

                        var isDragging = false
                        val downTime = System.currentTimeMillis()
                        var longPressTriggered = false
                        var endDate: String? = null

                        while (true) {
                            val ev = awaitPointerEvent()
                            val pos = ev.changes.firstOrNull()?.position ?: break
                            val elapsed = System.currentTimeMillis() - downTime

                            if (!longPressTriggered) {
                                if (elapsed >= longPressMs) {
                                    longPressTriggered = true
                                    isDragging = true

                                    onDragStart()

                                    val idx = nearestIdx(pos.x)

                                    if (idx >= 0) {
                                        lastDragIdx = idx
                                        endDate = points[idx].date
                                        onDragMove(endDate)

                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            } else {
                                val idx = nearestIdx(pos.x)

                                if (idx >= 0 && idx != lastDragIdx) {
                                    lastDragIdx = idx
                                    endDate = points[idx].date
                                    onDragMove(endDate)

                                    if (hapticEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }

                            ev.changes.forEach { it.consume() }

                            if (ev.changes.none { it.pressed }) {
                                if (isDragging) {
                                    onDragEnd(endDate)
                                } else {
                                    val idx = nearestIdx(down.position.x)

                                    if (idx >= 0) {
                                        onPointTapped(points[idx].date)

                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }

                                break
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = hPadPx

            val minScore = filledScores.minOf { it }.toFloat() - 4f
            val maxScore = filledScores.maxOf { it }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i ->
                pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1)
            }

            computedXs = xs

            val ys = filledScores.map { score ->
                h - pad - (h - 2 * pad) * (score - minScore) / span
            }

            val areaPath = Path().apply {
                moveTo(xs.first(), h - pad)
                lineTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }

                lineTo(xs.last(), h - pad)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorGreenPrimary.copy(alpha = 0.35f),
                        colorGreenPrimary.copy(alpha = 0f)
                    )
                )
            )

            val linePath = Path().apply {
                moveTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }
            }

            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(colorGreenPrimary, colorAccentDark),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            for (i in points.indices) {
                val fraction = if (i == highlightIndex) (1f - abs(i - animatedHighlight)).coerceIn(0f, 1f) else 0f

                val dotColor = if (hasData[i]) {
                    if (fraction > 0.5f) colorGreenSecondary else colorAccentDark
                } else {
                    colorTextTertiary
                }

                val outerRadius = lerp(12f, 28f, fraction)
                val innerRadius = lerp(8f, 19f, fraction)

                drawCircle(
                    color = BrandWhite,
                    radius = outerRadius,
                    center = Offset(xs[i], ys[i])
                )

                drawCircle(
                    color = dotColor,
                    radius = innerRadius,
                    center = Offset(xs[i], ys[i])
                )
            }
        }

        // Canvas 포인트 절대 X = 2*hPadPx + (fullWidth - 4*hPadPx) * i/(n-1)
        // BoxWithConstraints 에 padding 없이 fullWidth 로 동일 공식 적용
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fullW = with(density) { maxWidth.toPx() }
            val n = points.size
            points.forEachIndexed { i, point ->
                val xPx = 2 * hPadPx + (fullW - 4 * hPadPx) * (if (n > 1) i.toFloat() / (n - 1) else 0.5f) - with(density) { 5.dp.toPx() }
                val xDp = with(density) { xPx.toDp() }
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = xDp)
                        .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: AnalysisItem,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontSizeScale.current.scale

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.valueText,
                fontSize = (26 * fontScale).sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 어휘 점수 카드. 백분위 점수 대신 언어 지표 4개(대명사 비율 등)를 종합한
 * 좋음/주의/위험 배지를 보여주고, 탭하면 지표별 상세 화면으로 이동한다.
 */
@Composable
private fun TextScoreCard(
    item: AnalysisItem,
    status: MarkerStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontSizeScale.current.scale

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = status.label,
                    fontSize = (26 * fontScale).sp,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "자세히 보기",
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size((26 * fontScale).dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "눌러서 자세히 보기",
                style = MaterialTheme.typography.bodySmall,
                color = AppColor.textTertiary
            )
        }
    }
}