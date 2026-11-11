package com.midas26.mobileapp.ui.home

import com.midas26.mobileapp.ui.theme.AppColor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.theme.*
import com.midas26.mobileapp.ui.tutorial.TutorialOverlay
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianHomeTutorialStep
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialScreen
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialViewModel

private val GuardianTutorialAccentColor = Color(0xFFC85E48)

enum class GuardianMenu {
    Analysis,
    Location,
    Settings
}

@Composable
fun GuardianHomeScreen(
    guardianTutorialViewModel: GuardianTutorialViewModel,
    guardianName: String,
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    patientScores: Map<Int, Int?> = emptyMap(),
    onMenuClick: (GuardianMenu) -> Unit = {}
) {
    val tutorialState by guardianTutorialViewModel.state.collectAsState()

    var selectedPatient by remember(patients) {
        mutableStateOf(patients.firstOrNull())
    }

    var linkedUserBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var todayScoreBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var analysisMenuBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var locationMenuBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    var settingsMenuBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GuardianHomeHeader(
                guardianName = guardianName,
                patients = patients,
                selectedPatient = selectedPatient,
                isLoading = isLoading,
                todayScore = selectedPatient?.userId?.let {
                    patientScores[it]
                },
                onPatientSelected = {
                    selectedPatient = it
                },
                onLinkedUserBoundsChanged = {
                    linkedUserBounds = it
                },
                onTodayScoreBoundsChanged = {
                    todayScoreBounds = it
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuardianMenuList(
                onMenuClick = onMenuClick,
                onAnalysisBoundsChanged = {
                    analysisMenuBounds = it
                },
                onLocationBoundsChanged = {
                    locationMenuBounds = it
                },
                onSettingsBoundsChanged = {
                    settingsMenuBounds = it
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (
            tutorialState.isRunning &&
            tutorialState.currentScreen ==
            GuardianTutorialScreen.HOME &&
            tutorialState.homeStep !=
            GuardianHomeTutorialStep.MOVE_TO_HOME_TAB &&
            tutorialState.homeStep !=
            GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB
        ) {
            val targetBounds = when (
                tutorialState.homeStep
            ) {
                GuardianHomeTutorialStep.LINKED_USER ->
                    linkedUserBounds

                GuardianHomeTutorialStep.TODAY_SCORE ->
                    todayScoreBounds

                GuardianHomeTutorialStep.ANALYSIS_MENU ->
                    analysisMenuBounds

                GuardianHomeTutorialStep.LOCATION_MENU ->
                    locationMenuBounds

                GuardianHomeTutorialStep.SETTINGS_MENU ->
                    settingsMenuBounds

                GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
                GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB,
                GuardianHomeTutorialStep.COMPLETED ->
                    null
            }

            if (targetBounds != null) {
                TutorialOverlay(
                    targetBounds = targetBounds,
                    title = guardianHomeTutorialTitle(
                        tutorialState.homeStep
                    ),
                    message = guardianHomeTutorialMessage(
                        tutorialState.homeStep
                    ),
                    currentStep =
                        tutorialState.currentNumber,
                    totalSteps =
                        tutorialState.totalNumber,
                    onNext = {
                        when (tutorialState.homeStep) {
                            GuardianHomeTutorialStep.LINKED_USER -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.TODAY_SCORE,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.TODAY_SCORE -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.ANALYSIS_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.ANALYSIS_MENU -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.LOCATION_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.LOCATION_MENU -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.SETTINGS_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.SETTINGS_MENU -> {
                                guardianTutorialViewModel
                                    .showAnalysisTabGuide(
                                        number =
                                            tutorialState.currentNumber + 1
                                    )
                            }

                            GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
                            GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB,
                            GuardianHomeTutorialStep.COMPLETED -> Unit
                        }
                    },
                    onTargetClick = {
                        when (tutorialState.homeStep) {
                            GuardianHomeTutorialStep.LINKED_USER -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.TODAY_SCORE,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.TODAY_SCORE -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.ANALYSIS_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.ANALYSIS_MENU -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.LOCATION_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.LOCATION_MENU -> {
                                guardianTutorialViewModel.moveToHome(
                                    step =
                                        GuardianHomeTutorialStep.SETTINGS_MENU,
                                    number =
                                        tutorialState.currentNumber + 1
                                )
                            }

                            GuardianHomeTutorialStep.SETTINGS_MENU -> {
                                guardianTutorialViewModel
                                    .showAnalysisTabGuide(
                                        number =
                                            tutorialState.currentNumber + 1
                                    )
                            }

                            GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
                            GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB,
                            GuardianHomeTutorialStep.COMPLETED -> Unit
                        }
                    },
                    onSkip = {
                        guardianTutorialViewModel.stopTutorial()
                    },
                    tutorialColor = GuardianTutorialAccentColor
                )
            }
        }
    }
}


@Composable
private fun GuardianHomeHeader(
    guardianName: String,
    patients: List<LinkedUserInfo>,
    selectedPatient: LinkedUserInfo?,
    isLoading: Boolean,
    todayScore: Int?,
    onPatientSelected: (LinkedUserInfo) -> Unit,
    onLinkedUserBoundsChanged: (Rect) -> Unit,
    onTodayScoreBoundsChanged: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppColor.guardianDark,
                        AppColor.guardianPrimary
                    )
                )
            )
    ) {
        // 배경 원 장식
        Box(
            modifier = Modifier
                .size(190.dp)
                .offset(x = 235.dp, y = 22.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.16f))
                .onGloballyPositioned { coordinates ->
                    onTodayScoreBoundsChanged(
                        coordinates.boundsInRoot()
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "오늘의 점수",
                    fontSize = 15.sp,
                    color = BrandWhite.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BrandWhite,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = todayScore?.toString() ?: "-",
                        fontSize = 52.sp,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = (-30).dp, y = 110.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp)
        ) {
            Text(
                text = "$guardianName ${stringResource(R.string.guardian_user_suffix)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )

            Spacer(modifier = Modifier.height(18.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = BrandWhite,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                patients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다",
                        fontSize = 16.sp,
                        color = BrandWhite.copy(alpha = 0.8f)
                    )
                }
                else -> {
                    LinkedPatientDropdown(
                        patients = patients,
                        selectedPatient = selectedPatient,
                        onPatientSelected = onPatientSelected,
                        onBoundsChanged = onLinkedUserBoundsChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedPatientDropdown(
    patients: List<LinkedUserInfo>,
    selectedPatient: LinkedUserInfo?,
    onPatientSelected: (LinkedUserInfo) -> Unit,
    onBoundsChanged: (Rect) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .onGloballyPositioned { coordinates ->
                    onBoundsChanged(
                        coordinates.boundsInRoot()
                    )
                }
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(24.dp),
            color = BrandWhite.copy(alpha = 0.22f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppColor.guardianDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedPatient?.name ?: "사용자 선택",
                    fontSize = 18.sp,
                    color = BrandWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " 님 연결됨",
                    fontSize = 18.sp,
                    color = BrandWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            patients.forEach { patient ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${patient.name ?: "이름 없음"} 님",
                            fontWeight = if (patient.userId == selectedPatient?.userId)
                                FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onPatientSelected(patient)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GuardianMenuList(
    onMenuClick: (GuardianMenu) -> Unit,
    onAnalysisBoundsChanged: (Rect) -> Unit,
    onLocationBoundsChanged: (Rect) -> Unit,
    onSettingsBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        SectionHeader(title = stringResource(R.string.guardian_menu))

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.BarChart,
            title = stringResource(R.string.guardian_menu_analysis),
            desc = "대화 결과와 인지 점수를 확인해요",
            onClick = {
                onMenuClick(GuardianMenu.Analysis)
            },
            onBoundsChanged = onAnalysisBoundsChanged,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.guardian_menu_location),
            desc = "보호 대상자의 현재 위치를 확인해요",
            onClick = {
                onMenuClick(GuardianMenu.Location)
            },
            onBoundsChanged = onLocationBoundsChanged,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        SectionHeader(title = stringResource(R.string.section_settings))

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.menu_settings),
            desc = "앱 환경과 알림을 설정해요",
            onClick = {
                onMenuClick(GuardianMenu.Settings)
            },
            onBoundsChanged = onSettingsBoundsChanged,
            accent = AppColor.surfaceElevated,
            iconTint = AppColor.textTertiary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
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
private fun GuardianMenuCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified
) {
    val resolvedAccent   = if (accent   == Color.Unspecified) AppColor.guardianSurface else accent
    val resolvedIconTint = if (iconTint == Color.Unspecified) AppColor.guardianDark    else iconTint

    Surface(
        modifier = modifier
            .height(104.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    coordinates.boundsInRoot()
                )
            }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }

            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(20.dp),
                color = resolvedAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedIconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

private fun guardianHomeTutorialTitle(
    step: GuardianHomeTutorialStep
): String {
    return when (step) {
        GuardianHomeTutorialStep.MOVE_TO_HOME_TAB ->
            "보호자 홈 화면"

        GuardianHomeTutorialStep.LINKED_USER ->
            "연결된 사용자 선택"

        GuardianHomeTutorialStep.TODAY_SCORE ->
            "오늘의 인지 점수"

        GuardianHomeTutorialStep.ANALYSIS_MENU ->
            "분석 결과"

        GuardianHomeTutorialStep.LOCATION_MENU ->
            "위치 정보"

        GuardianHomeTutorialStep.SETTINGS_MENU ->
            "보호자 설정"

        GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB ->
            "분석 탭으로 이동하기"

        GuardianHomeTutorialStep.COMPLETED ->
            "보호자 홈 사용법 완료"
    }
}

private fun guardianHomeTutorialMessage(
    step: GuardianHomeTutorialStep
): String {
    return when (step) {
        GuardianHomeTutorialStep.MOVE_TO_HOME_TAB ->
            "홈 탭에서는 연결된 사용자와 오늘의 점수를 확인할 수 있어요."

        GuardianHomeTutorialStep.LINKED_USER ->
            "여기를 눌러 현재 확인할 사용자를 선택할 수 있어요."

        GuardianHomeTutorialStep.TODAY_SCORE ->
            "선택한 사용자의 오늘 인지 점수가 표시돼요."

        GuardianHomeTutorialStep.ANALYSIS_MENU ->
            "대화 결과와 인지 점수의 상세 분석을 확인할 수 있어요."

        GuardianHomeTutorialStep.LOCATION_MENU ->
            "보호 대상자의 현재 위치와 이동 정보를 확인할 수 있어요."

        GuardianHomeTutorialStep.SETTINGS_MENU ->
            "관리 사용자, 알림과 접근성 등 보호자 앱 설정을 변경할 수 있어요."

        GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB ->
            "하단 분석 탭을 눌러 연결된 사용자의 분석 결과를 확인해 보세요."

        GuardianHomeTutorialStep.COMPLETED ->
            "보호자 홈 화면의 주요 기능 안내가 끝났어요."
    }
}