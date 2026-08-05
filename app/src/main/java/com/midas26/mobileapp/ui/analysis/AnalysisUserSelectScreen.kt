package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.guardian.PatientAnalysisStatus
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.tutorial.TutorialOverlay
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianAnalysisTutorialStep
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialScreen
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialViewModel
import com.midas26.mobileapp.util.PrefsManager

private val GuardianTutorialAccentColor = Color(0xFFC85E48)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisUserSelectScreen(
    guardianTutorialViewModel: GuardianTutorialViewModel,
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    patientStatuses: Map<Int, PatientAnalysisStatus> = emptyMap(),
    onBack: () -> Unit,
    onUserClick: (LinkedUserInfo) -> Unit
) {
    val tutorialState by guardianTutorialViewModel.state.collectAsState()

    var guideBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var firstUserBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnalysisChartIcon(
                            color = AppColor.textPrimary,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "분석 결과 확인",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = AppColor.textPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                patients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다",
                        color = AppColor.textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                )
                                .onGloballyPositioned { coordinates ->
                                    guideBounds = coordinates.boundsInRoot()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFE5DF)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 16.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "분석 결과를 확인할 사용자를 선택하세요!",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC85E48)
                                )
                            }
                        }

                        patients.forEachIndexed { index, patient ->
                            val status = patient.userId?.let {
                                patientStatuses[it]
                            }

                            AnalysisUserCard(
                                patient = patient,
                                status = status,
                                onBoundsChanged = { bounds ->
                                    if (index == 0) {
                                        firstUserBounds = bounds
                                    }
                                },
                                onClick = {
                                    onUserClick(patient)
                                }
                            )
                        }
                    }

                    VerticalScrollbar(
                        state = scrollState,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            if (
                tutorialState.isRunning &&
                tutorialState.currentScreen ==
                GuardianTutorialScreen.ANALYSIS_SELECT &&
                tutorialState.analysisStep !=
                GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB &&
                tutorialState.analysisStep !=
                GuardianAnalysisTutorialStep.COMPLETED
            ) {
                val targetBounds = when (
                    tutorialState.analysisStep
                ) {
                    GuardianAnalysisTutorialStep.USER_SELECT ->
                        firstUserBounds

                    GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB,
                    GuardianAnalysisTutorialStep.COMPLETED ->
                        null

                    else -> null
                }

                val firstPatient = patients.firstOrNull()

                if (targetBounds != null && firstPatient != null) {
                    val openFirstUserResult = {
                        guardianTutorialViewModel.moveToAnalysisResult(
                            number = tutorialState.currentNumber + 1
                        )
                        onUserClick(firstPatient)
                    }

                    TutorialOverlay(
                        targetBounds = targetBounds,
                        title = "분석할 사용자 선택",
                        message =
                            "연결된 사용자 중 분석 결과를 확인할 사용자를 선택할 수 있어요.",
                        currentStep =
                            tutorialState.currentNumber,
                        totalSteps =
                            tutorialState.totalNumber,
                        onNext = openFirstUserResult,
                        onTargetClick = openFirstUserResult,
                        onSkip = {
                            guardianTutorialViewModel.stopTutorial()
                        },
                        tutorialColor = GuardianTutorialAccentColor
                    )
                }
            }
        }
    }
}

/**
 * 첨부된 이미지와 비슷한 형태의 막대그래프 아이콘
 *
 * color에 제목 글자와 동일한 색상을 전달하여
 * 아이콘과 제목 색상이 함께 변경되도록 구성
 */
@Composable
private fun AnalysisChartIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val iconWidth = size.width
        val iconHeight = size.height

        val barWidth = iconWidth * 0.20f
        val bottomPosition = iconHeight * 0.88f

        // 왼쪽 막대
        drawRect(
            color = color,
            topLeft = Offset(
                x = iconWidth * 0.08f,
                y = iconHeight * 0.42f
            ),
            size = Size(
                width = barWidth,
                height = bottomPosition - iconHeight * 0.42f
            )
        )

        // 가운데 막대
        drawRect(
            color = color,
            topLeft = Offset(
                x = iconWidth * 0.40f,
                y = iconHeight * 0.14f
            ),
            size = Size(
                width = barWidth,
                height = bottomPosition - iconHeight * 0.14f
            )
        )

        // 오른쪽 막대
        drawRect(
            color = color,
            topLeft = Offset(
                x = iconWidth * 0.72f,
                y = iconHeight * 0.58f
            ),
            size = Size(
                width = barWidth,
                height = bottomPosition - iconHeight * 0.58f
            )
        )
    }
}

@Composable
private fun AnalysisUserCard(
    patient: LinkedUserInfo,
    status: PatientAnalysisStatus?,
    onBoundsChanged: (Rect) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val relation = patient.userId
        ?.let {
            PrefsManager.from(context).getPatientRelation(it)
        }
        ?.ifEmpty {
            "사용자"
        }
        ?: "사용자"

    val pillFontSize = with(LocalDensity.current) {
        14.dp.toSp()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp
            )
            .height(100.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    coordinates.boundsInRoot()
                )
            }
            .clickable {
                onClick()
            }
            .border(
                width = 1.5.dp,
                color = AppColor.guardianDark,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 이름과 관계 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = patient.name ?: "이름 없음",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AppColor.guardianSurface,
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        Text(
                            text = relation,
                            fontSize = pillFontSize,
                            lineHeight = pillFontSize,
                            fontWeight = FontWeight.Medium,
                            color = AppColor.guardianDark,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // 분석 결과 상태 문구
                when (status) {
                    PatientAnalysisStatus.LOADING -> {
                        StatusShimmer()
                    }

                    PatientAnalysisStatus.NEW_RESULT -> {
                        Text(
                            text = "새로운 분석 결과가 나왔어요!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    PatientAnalysisStatus.VIEWED_TODAY -> {
                        Text(
                            text = "오늘의 분석 결과를 확인했어요!",
                            fontSize = 13.sp,
                            color = AppColor.textTertiary
                        )
                    }

                    PatientAnalysisStatus.NO_RESULT,
                    null -> {
                        Text(
                            text = "아직 오늘의 분석 결과가 도착하지 않았어요",
                            fontSize = 13.sp,
                            color = AppColor.textTertiary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "분석 결과 확인",
                tint = AppColor.textTertiary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * 상태 로딩 중 shimmer 효과
 */
@Composable
private fun StatusShimmer() {
    val transition = rememberInfiniteTransition(
        label = "shimmer"
    )

    val shimmerX by transition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(13.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE0E0E0),
                        Color(0xFFF5F5F5),
                        Color(0xFFE0E0E0)
                    ),
                    start = Offset(
                        x = shimmerX,
                        y = 0f
                    ),
                    end = Offset(
                        x = shimmerX + 200f,
                        y = 0f
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
    )
}