package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.guardian.PatientAnalysisStatus
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
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

    // 분석 튜토리얼에서는 실제 연결 사용자 대신 예시 사용자만 표시한다.
    val isAnalysisTutorial = tutorialState.isRunning &&
            tutorialState.currentScreen == GuardianTutorialScreen.ANALYSIS_SELECT
    val demoPatient = remember {
        LinkedUserInfo(
            userId = null,
            phone = null,
            name = "예시 사용자",
            role = "USER",
            patientCode = null
        )
    }
    val visiblePatients = if (isAnalysisTutorial) listOf(demoPatient) else patients

    // 접근성 글자 크기 확대 시 화면 넘침을 방지한다.
    val fontScale = LocalFontSizeScale.current.scale
    val screenFontScale = fontScale.coerceAtMost(1.35f)

    var firstUserBounds by remember(isAnalysisTutorial) {
        mutableStateOf<Rect?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "분석 결과 확인",
                        fontWeight = FontWeight.Bold,
                        fontSize = (22 * screenFontScale).sp,
                        color = AppColor.textPrimary,
                        maxLines = 1
                    )
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
                isLoading && !isAnalysisTutorial -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                visiblePatients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다",
                        fontSize = (16 * screenFontScale).sp,
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
                                ),
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
                                    fontSize = (18 * screenFontScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC85E48)
                                )
                            }
                        }

                        if (isAnalysisTutorial) {
                            Text(
                                text = "튜토리얼 예시 사용자입니다. 실제 연결된 사용자가 아닙니다.",
                                fontSize = (13 * screenFontScale).sp,
                                color = AppColor.textSecondary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                        }

                        visiblePatients.forEachIndexed { index, patient ->
                            val status = patient.userId?.let {
                                patientStatuses[it]
                            }

                            AnalysisUserCard(
                                patient = patient,
                                status = status,
                                fontScale = fontScale,
                                isTutorialExample = isAnalysisTutorial,
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

                val firstPatient = visiblePatients.firstOrNull()

                if (targetBounds != null && firstPatient != null) {
                    // 튜토리얼 단계 전환과 화면 이동은 AppNavGraph에서 처리한다.
                    val openFirstUserResult = {
                        onUserClick(firstPatient)
                    }

                    TutorialOverlay(
                        targetBounds = targetBounds,
                        title = "분석할 사용자 선택",
                        message =
                            "예시 사용자를 선택해 분석 결과 화면을 살펴볼게요. 표시되는 점수는 실제 결과가 아닙니다.",
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

@Composable
private fun AnalysisUserCard(
    patient: LinkedUserInfo,
    status: PatientAnalysisStatus?,
    fontScale: Float,
    isTutorialExample: Boolean = false,
    onBoundsChanged: (Rect) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val relation = if (isTutorialExample) "예시" else patient.userId
        ?.let {
            PrefsManager.from(context).getPatientRelation(it)
        }
        ?.ifEmpty {
            "사용자"
        }
        ?: "사용자"

    // 카드 내부 텍스트가 잘리지 않도록 확대 비율을 제한한다.
    val cardFontScale = fontScale.coerceAtMost(1.25f)
    val cardHeight = (100f + ((cardFontScale - 1f) * 56f)).dp
    val pillFontSize = (14 * cardFontScale).sp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp
            )
            .height(cardHeight)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = patient.name ?: "이름 없음",
                        fontSize = (25 * cardFontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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

                // 예시 사용자에게는 실제 분석 상태를 표시하지 않는다.
                if (isTutorialExample) {
                    Text(
                        text = "튜토리얼용 가상 분석 결과 보기",
                        fontSize = (13 * cardFontScale).sp,
                        color = AppColor.textSecondary
                    )
                } else when (status) {
                    PatientAnalysisStatus.LOADING -> {
                        StatusShimmer()
                    }

                    PatientAnalysisStatus.NEW_RESULT -> {
                        Text(
                            text = "새로운 분석 결과가 나왔어요!",
                            fontSize = (13 * cardFontScale).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    PatientAnalysisStatus.VIEWED_TODAY -> {
                        Text(
                            text = "오늘의 분석 결과를 확인했어요!",
                            fontSize = (13 * cardFontScale).sp,
                            color = AppColor.textTertiary
                        )
                    }

                    PatientAnalysisStatus.NO_RESULT,
                    null -> {
                        Text(
                            text = "아직 오늘의 분석 결과가 도착하지 않았어요",
                            fontSize = (13 * cardFontScale).sp,
                            color = AppColor.textTertiary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "분석 결과 확인",
                tint = AppColor.textTertiary,
                modifier = Modifier.size((26 * cardFontScale.coerceAtMost(1.15f)).dp)
            )
        }
    }
}

/** 분석 상태 조회 중 표시하는 로딩 효과. */
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
