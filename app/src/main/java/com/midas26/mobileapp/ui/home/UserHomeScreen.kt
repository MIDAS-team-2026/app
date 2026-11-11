package com.midas26.mobileapp.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.tutorial.HomeTutorialStep
import com.midas26.mobileapp.ui.tutorial.TutorialScreen
import com.midas26.mobileapp.ui.tutorial.TutorialViewModel

@Composable
fun UserHomeScreen(
    tutorialViewModel: TutorialViewModel,
    userName: String = "홍길동",
    streakDays: Int = 4,
    weeklyChecks: List<Boolean> = List(7) { false },
    weeklyDayLabels: List<String> = listOf("일", "월", "화", "수", "목", "금", "토"),
    todayIndex: Int = 6,
    onMenuClick: (UserMenu) -> Unit = {}
) {
    val tutorialState by tutorialViewModel.state.collectAsState()

    /*
     * 현재 Compose 버전에서 localBoundingBoxOf를 지원하지 않을 수 있으므로
     * 최상위 화면의 boundsInRoot 값을 저장한 뒤 대상 좌표에서 빼서
     * 오버레이 내부 좌표로 변환합니다.
     */
    var tutorialRootBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var weeklyCheckBounds by remember { mutableStateOf<Rect?>(null) }
    var voiceChatBounds by remember { mutableStateOf<Rect?>(null) }
    var analysisBounds by remember { mutableStateOf<Rect?>(null) }
    var settingsBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coordinates ->
                tutorialRootBounds = coordinates.boundsInRoot()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            UserHomeHeader(
                userName = userName,
                streakDays = streakDays,
                weeklyChecks = weeklyChecks,
                weeklyDayLabels = weeklyDayLabels,
                todayIndex = todayIndex,
                weekStatusModifier = Modifier.onGloballyPositioned { coordinates ->
                    weeklyCheckBounds = coordinates
                        .boundsInRoot()
                        .relativeTo(tutorialRootBounds)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            MenuGrid(
                onMenuClick = onMenuClick,
                voiceChatModifier = Modifier.onGloballyPositioned { coordinates ->
                    voiceChatBounds = coordinates
                        .boundsInRoot()
                        .relativeTo(tutorialRootBounds)
                },
                analysisModifier = Modifier.onGloballyPositioned { coordinates ->
                    analysisBounds = coordinates
                        .boundsInRoot()
                        .relativeTo(tutorialRootBounds)
                },
                settingsModifier = Modifier.onGloballyPositioned { coordinates ->
                    settingsBounds = coordinates
                        .boundsInRoot()
                        .relativeTo(tutorialRootBounds)
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (
            tutorialState.isRunning &&
            tutorialState.currentScreen == TutorialScreen.HOME &&
            tutorialState.homeStep != HomeTutorialStep.MOVE_TO_HOME_TAB &&
            tutorialState.homeStep != HomeTutorialStep.MOVE_TO_VOICE_TAB
        ) {
            val targetBounds = when (tutorialState.homeStep) {
                HomeTutorialStep.MOVE_TO_HOME_TAB -> null
                HomeTutorialStep.WELCOME -> null
                HomeTutorialStep.WEEKLY_CHECK -> weeklyCheckBounds
                HomeTutorialStep.VOICE_CHAT -> voiceChatBounds
                HomeTutorialStep.ANALYSIS -> analysisBounds
                HomeTutorialStep.SETTINGS -> settingsBounds
                HomeTutorialStep.MOVE_TO_VOICE_TAB -> null
                HomeTutorialStep.COMPLETED -> null
            }

            HomeTutorialOverlay(
                step = tutorialState.homeStep,
                targetBounds = targetBounds,
                currentNumber = tutorialState.currentNumber,
                totalNumber = tutorialState.totalNumber,
                onNext = {
                    when (tutorialState.homeStep) {
                        HomeTutorialStep.MOVE_TO_HOME_TAB -> {
                            // AppNavGraph에서 하단 홈 탭 안내와 클릭을 처리합니다.
                        }

                        HomeTutorialStep.WELCOME -> {
                            tutorialViewModel.moveHomeStep(
                                step = HomeTutorialStep.WEEKLY_CHECK,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        HomeTutorialStep.WEEKLY_CHECK -> {
                            tutorialViewModel.moveHomeStep(
                                step = HomeTutorialStep.VOICE_CHAT,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        HomeTutorialStep.VOICE_CHAT -> {
                            tutorialViewModel.moveHomeStep(
                                step = HomeTutorialStep.ANALYSIS,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        HomeTutorialStep.ANALYSIS -> {
                            tutorialViewModel.moveHomeStep(
                                step = HomeTutorialStep.SETTINGS,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        HomeTutorialStep.SETTINGS -> {
                            if (tutorialViewModel.isFullTutorial()) {
                                /*
                                 * 홈 설명이 끝나면 바로 화면을 이동하지 않고
                                 * AppNavGraph에서 하단 대화 탭을 안내합니다.
                                 */
                                tutorialViewModel.showVoiceTabGuide(
                                    number = tutorialState.currentNumber + 1
                                )
                            } else {
                                tutorialViewModel.completeTutorial()
                            }
                        }

                        HomeTutorialStep.MOVE_TO_VOICE_TAB -> {
                            /*
                             * 하단 탭 오버레이와 실제 이동은 AppNavGraph에서 처리합니다.
                             */
                        }

                        HomeTutorialStep.COMPLETED -> {
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


/**
 * boundsInRoot()로 얻은 대상 Rect를 현재 화면 최상위 Box 기준 좌표로 변환합니다.
 *
 * rootBounds가 아직 측정되지 않은 첫 프레임에는 null을 반환하며,
 * 다음 onGloballyPositioned 호출에서 정상 좌표가 저장됩니다.
 */
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
private fun HomeTutorialOverlay(
    step: HomeTutorialStep,
    targetBounds: Rect?,
    currentNumber: Int,
    totalNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current

    /*
     * AppColor의 일부 색상 값이 @Composable getter일 수 있으므로
     * Canvas DrawScope 내부에서 직접 호출하지 않고 미리 읽어 둡니다.
     */
    val highlightColor = AppColor.greenPrimary

    val title = when (step) {
        HomeTutorialStep.MOVE_TO_HOME_TAB -> "홈 화면 살펴보기"
        HomeTutorialStep.WELCOME -> "똑똑 사용법을 알아볼까요?"
        HomeTutorialStep.WEEKLY_CHECK -> "요일별 음성 점검"
        HomeTutorialStep.VOICE_CHAT -> "음성 대화"
        HomeTutorialStep.ANALYSIS -> "분석 결과"
        HomeTutorialStep.SETTINGS -> "설정"
        HomeTutorialStep.MOVE_TO_VOICE_TAB -> "음성 대화 시작하기"
        HomeTutorialStep.COMPLETED -> "홈 화면 사용법 완료"
    }

    val description = when (step) {
        HomeTutorialStep.MOVE_TO_HOME_TAB ->
            "홈 탭에서는 오늘의 점검 상태와 주요 기능을 확인할 수 있어요."

        HomeTutorialStep.WELCOME ->
            "화면의 주요 기능을 하나씩 천천히 알려드릴게요."

        HomeTutorialStep.WEEKLY_CHECK ->
            "이번 주에 음성 대화를 진행했는지 요일별로 확인할 수 있어요."

        HomeTutorialStep.VOICE_CHAT ->
            "이 버튼을 누르면 또바기와 음성으로 대화할 수 있어요."

        HomeTutorialStep.ANALYSIS ->
            "인지 점수와 주간 분석 결과를 확인할 수 있어요."

        HomeTutorialStep.SETTINGS ->
            "접근성, 위치 공유, 알림 등 앱 설정을 변경할 수 있어요."

        HomeTutorialStep.MOVE_TO_VOICE_TAB ->
            "하단 대화 탭을 누르면 또바기와 음성으로 대화할 수 있어요."

        HomeTutorialStep.COMPLETED ->
            "홈 화면의 주요 기능을 모두 살펴봤어요."
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {})
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val showPopupAtTop = targetBounds?.let { bounds ->
            bounds.center.y > screenHeightPx * 0.54f
        } ?: false

        val popupAlignment = when {
            step == HomeTutorialStep.WELCOME -> Alignment.Center
            showPopupAtTop -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            drawRect(
                color = Color.Black.copy(alpha = 0.58f)
            )

            targetBounds?.let { bounds ->
                val outerPadding = 5.dp.toPx()
                val left = (bounds.left - outerPadding).coerceAtLeast(0f)
                val top = (bounds.top - outerPadding).coerceAtLeast(0f)
                val right = (bounds.right + outerPadding).coerceAtMost(size.width)
                val bottom = (bounds.bottom + outerPadding).coerceAtMost(size.height)

                val highlightTopLeft = Offset(left, top)
                val highlightSize = Size(
                    width = (right - left).coerceAtLeast(0f),
                    height = (bottom - top).coerceAtLeast(0f)
                )
                val cornerRadius = CornerRadius(
                    x = 22.dp.toPx(),
                    y = 22.dp.toPx()
                )

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = highlightTopLeft,
                    size = highlightSize,
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = highlightColor,
                    topLeft = highlightTopLeft,
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
                    color = AppColor.textSecondary,
                    textAlign = if (step == HomeTutorialStep.WELCOME) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                                step == HomeTutorialStep.SETTINGS ||
                                step == HomeTutorialStep.COMPLETED
                            ) {
                                "다음"
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
private fun UserHomeHeader(
    userName: String,
    streakDays: Int,
    weeklyChecks: List<Boolean>,
    weeklyDayLabels: List<String>,
    todayIndex: Int,
    weekStatusModifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(AppColor.accentDark, AppColor.greenPrimary)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = userName,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = " ${stringResource(R.string.home_user_suffix)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            WeekStatusCard(
                streakDays = streakDays,
                weeklyChecks = weeklyChecks,
                weeklyDayLabels = weeklyDayLabels,
                todayIndex = todayIndex,
                modifier = weekStatusModifier
            )
        }
    }
}

@Composable
private fun WeekStatusCard(
    streakDays: Int,
    weeklyChecks: List<Boolean>,
    weeklyDayLabels: List<String>,
    todayIndex: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, BrandWhite.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 16.dp
            )
        ) {
            Text(
                text = if (streakDays > 0) {
                    stringResource(R.string.home_streak, streakDays)
                } else {
                    stringResource(R.string.home_streak_motivation)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyDayLabels.forEachIndexed { index, label ->
                    DayStatusDot(
                        dayLabel = label,
                        checked = weeklyChecks.getOrNull(index) == true,
                        isToday = index == todayIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun DayStatusDot(
    dayLabel: String,
    checked: Boolean,
    isToday: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = BrandWhite.copy(alpha = 0.85f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (isToday) {
                        Modifier.border(2.dp, BrandWhite, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            shape = CircleShape,
            color = when {
                isToday -> Color.Transparent
                checked -> BrandWhite.copy(alpha = 0.4f)
                else -> BrandWhite.copy(alpha = 0.15f)
            }
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Text(
                        text = "✓",
                        color = BrandWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColor.divider)
        )
    }
}

@Composable
private fun MenuGrid(
    onMenuClick: (UserMenu) -> Unit,
    voiceChatModifier: Modifier = Modifier,
    analysisModifier: Modifier = Modifier,
    settingsModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        SectionHeader(
            title = stringResource(R.string.home_today_check)
        )

        Spacer(modifier = Modifier.height(12.dp))

        MenuCard(
            icon = Icons.Default.Mic,
            titleRes = R.string.menu_voice_chat,
            descRes = R.string.menu_voice_chat_desc,
            onClick = {
                onMenuClick(UserMenu.VoiceChat)
            },
            modifier = voiceChatModifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        MenuCard(
            icon = Icons.Default.BarChart,
            titleRes = R.string.menu_analysis,
            descRes = R.string.menu_analysis_desc,
            onClick = {
                onMenuClick(UserMenu.Analysis)
            },
            modifier = analysisModifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        SectionHeader(
            title = stringResource(R.string.section_settings)
        )

        Spacer(modifier = Modifier.height(12.dp))

        MenuCard(
            icon = Icons.Default.Settings,
            titleRes = R.string.menu_settings,
            descRes = R.string.menu_settings_desc,
            onClick = {
                onMenuClick(UserMenu.Settings)
            },
            accent = AppColor.surfaceElevated,
            iconTint = AppColor.textTertiary,
            modifier = settingsModifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    titleRes: Int,
    descRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColor.greenSurface,
    iconTint: Color = AppColor.accentDark
) {
    Surface(
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }

            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(20.dp),
                color = accent
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

enum class UserMenu {
    VoiceChat,
    Recall,
    Analysis,
    Settings
}