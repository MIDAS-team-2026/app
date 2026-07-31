package com.midas26.mobileapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.R
import com.midas26.mobileapp.location.LocationForegroundService
import com.midas26.mobileapp.notification.AlarmScheduler
import com.midas26.mobileapp.notification.NotificationHelper
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.tutorial.SettingsTutorialStep
import com.midas26.mobileapp.ui.tutorial.TutorialReplayTarget
import com.midas26.mobileapp.ui.tutorial.TutorialScreen
import com.midas26.mobileapp.ui.tutorial.TutorialViewModel
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SettingsScreen(
    tutorialViewModel: TutorialViewModel,
    userName: String = "홍길동",
    weeklyScore: Int = 75,
    streakDays: Int = 4,
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onAccessibility: () -> Unit = {},
    onProfileEdit: () -> Unit = {},
    onReplayTutorial: (TutorialReplayTarget) -> Unit = {},
    profileViewModel: ProfileEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)

    val tutorialState by tutorialViewModel.state.collectAsState()

    /*
     * 현재 프로젝트의 Compose 버전에서 localBoundingBoxOf를 지원하지 않을 수 있으므로
     * 최상위 화면의 boundsInRoot 값을 저장합니다.
     *
     * 각 강조 대상도 boundsInRoot로 측정한 뒤 루트 위치를 빼서
     * SettingsScreen 내부 오버레이 좌표로 변환합니다.
     */
    var tutorialRootBounds by remember { mutableStateOf<Rect?>(null) }

    // 튜토리얼에서 강조할 실제 UI 위치입니다.
    var accessibilityBounds by remember { mutableStateOf<Rect?>(null) }
    var locationSharingBounds by remember { mutableStateOf<Rect?>(null) }
    var notificationBounds by remember { mutableStateOf<Rect?>(null) }
    var notificationTimeBounds by remember { mutableStateOf<Rect?>(null) }
    var supportBounds by remember { mutableStateOf<Rect?>(null) }
    val role = prefs.getUserRole()
    val isGuardian = role == PrefsManager.ROLE_GUARDIAN
    val isPatient = role == PrefsManager.ROLE_USER
    val userId = prefs.getUserId()
    val protectors by profileViewModel.protectors.collectAsState()

    val guardianLabel = when {
        protectors.isEmpty() -> "없음"
        protectors.size == 1 -> protectors[0].name ?: "보호자"
        else -> "${protectors[0].name ?: "보호자"} +${protectors.size - 1}"
    }

    LaunchedEffect(userId) {
        if (isPatient && userId > 0) {
            profileViewModel.loadProtectors(userId)
        }
    }

    var locationSharingEnabled by remember { mutableStateOf(prefs.getLocationSharingEnabled()) }
    val savedNotificationEnabled = remember { prefs.getNotificationEnabled() }
    var notificationEnabled by remember { mutableStateOf(savedNotificationEnabled) }
    var notifHour by remember { mutableIntStateOf(prefs.getNotificationHour()) }
    var notifMinute by remember { mutableIntStateOf(prefs.getNotificationMinute()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showTutorialSelectionDialog by remember { mutableStateOf(false) }
    var showLocationPermissionDialog by remember { mutableStateOf(false) }
    var locationPermissionPermanentlyDenied by remember { mutableStateOf(false) }

    NotificationHelper.createChannel(context)

    if (showTimePicker) {
        WheelTimePickerDialog(
            title = "알림 시간",
            initialHour = notifHour,
            initialMinute = notifMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                notifHour = hour
                notifMinute = minute
                prefs.setNotificationTime(hour, minute)
                AlarmScheduler.schedule(context, hour, minute)
                showTimePicker = false
            }
        )
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs.setLocationSharingEnabled(true)
            startLocationService(context)
        } else {
            val canAskAgain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (context as? androidx.activity.ComponentActivity)
                    ?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    ?: false
            } else false
            locationPermissionPermanentlyDenied = !canAskAgain
            showLocationPermissionDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs.setNotificationEnabled(true)
            AlarmScheduler.schedule(context, notifHour, notifMinute)
        } else {
            notificationEnabled = false
            Toast.makeText(context, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()
    val rangePx = with(LocalDensity.current) { 180.dp.toPx() }
    val p = (scrollState.value / rangePx).coerceIn(0f, 1f)

    LaunchedEffect(
        tutorialState.isRunning,
        tutorialState.currentScreen,
        tutorialState.settingsStep
    ) {
        if (
            tutorialState.isRunning &&
            tutorialState.currentScreen == TutorialScreen.SETTINGS
        ) {
            when (tutorialState.settingsStep) {
                SettingsTutorialStep.ACCESSIBILITY -> {
                    scrollState.animateScrollTo(0)
                }

                SettingsTutorialStep.LOCATION_SHARING -> {
                    scrollState.animateScrollTo(0)
                }

                SettingsTutorialStep.CHECK_NOTIFICATION -> {
                    scrollState.animateScrollTo(90)
                }

                SettingsTutorialStep.NOTIFICATION_TIME -> {
                    /*
                     * 알림 시간이 화면에 보이도록 화면 표시 상태만 임시로 엽니다.
                     * SharedPreferences의 실제 알림 설정 값은 변경하지 않습니다.
                     */
                    notificationEnabled = true
                    scrollState.animateScrollTo(190)
                }

                SettingsTutorialStep.SUPPORT -> {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                SettingsTutorialStep.COMPLETED -> Unit
            }
        }
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = "로그아웃",
            message = "정말 로그아웃 하시겠어요?",
            confirmText = "로그아웃",
            isGuardian = isGuardian,
            onConfirm = {
                PrefsManager.from(context).clearToken()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showTutorialSelectionDialog) {
        TutorialSelectionDialog(
            onDismiss = {
                showTutorialSelectionDialog = false
            },
            onSelect = { target ->
                showTutorialSelectionDialog = false
                onReplayTutorial(target)
            }
        )
    }

    if (showLocationPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showLocationPermissionDialog = false
                locationSharingEnabled = false
            },
            title = { Text("위치 권한 필요") },
            text = {
                Text(
                    if (locationPermissionPermanentlyDenied)
                        "위치 권한이 거부되었습니다.\n설정 앱에서 '항상 허용'으로 변경해 주세요."
                    else
                        "보호자에게 위치를 공유하려면\n'항상 허용' 위치 권한이 필요합니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPermissionDialog = false
                    if (locationPermissionPermanentlyDenied) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                        locationSharingEnabled = false
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }) {
                    Text(if (locationPermissionPermanentlyDenied) "설정으로 이동" else "다시 요청")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationPermissionDialog = false
                    locationSharingEnabled = false
                }) {
                    Text("취소")
                }
            }
        )
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(252.dp))
            Spacer(modifier = Modifier.height(10.dp))

            SettingsSection(
                title = "접근성"
            ) {
                SettingsRow(
                    label = "접근성 설정",
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        accessibilityBounds = coordinates
                            .boundsInRoot()
                            .relativeTo(tutorialRootBounds)
                    },
                    onClick = onAccessibility
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "앱 설정") {
                if (!isGuardian) {
                    SettingsToggleRow(
                        label = "보호자에게 위치 정보 제공",
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            locationSharingBounds = coordinates
                                .boundsInRoot()
                                .relativeTo(tutorialRootBounds)
                        },
                        description = "보호자가 내 위치를 확인할 수 있어요",
                        checked = locationSharingEnabled,
                        onCheckedChange = { enabled ->
                            locationSharingEnabled = enabled

                            if (enabled) {
                                val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED

                                if (bgGranted) {
                                    prefs.setLocationSharingEnabled(true)
                                    startLocationService(context)
                                } else {
                                    backgroundLocationLauncher.launch(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    )
                                }
                            } else {
                                prefs.setLocationSharingEnabled(false)
                                context.stopService(
                                    Intent(context, LocationForegroundService::class.java)
                                )
                            }
                        }
                    )

                    HorizontalDivider(
                        color = AppColor.divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                SettingsToggleRow(
                    label = "점검 알림",
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        notificationBounds = coordinates
                            .boundsInRoot()
                            .relativeTo(tutorialRootBounds)
                    },
                    description = "매일 점검 시간에 알림을 받아요",
                    checked = notificationEnabled,
                    onCheckedChange = { enabled ->
                        notificationEnabled = enabled

                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                prefs.setNotificationEnabled(true)
                                AlarmScheduler.schedule(context, notifHour, notifMinute)
                            }
                        } else {
                            prefs.setNotificationEnabled(false)
                            AlarmScheduler.cancel(context)
                        }
                    }
                )

                AnimatedVisibility(visible = notificationEnabled) {
                    Column {
                        HorizontalDivider(
                            color = AppColor.divider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        NotifTimeRow(
                            title = "알림 시간",
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                notificationTimeBounds = coordinates
                                    .boundsInRoot()
                                    .relativeTo(tutorialRootBounds)
                            },
                            hour = notifHour,
                            minute = notifMinute,
                            onClick = { showTimePicker = true }
                        )
                    }
                }

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    label = "앱 사용법 다시 보기",
                    onClick = {
                        showTutorialSelectionDialog = true
                    }
                )

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    label = "앱 버전",
                    trailingText = "1.0.0",
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(
                title = "지원",
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    supportBounds = coordinates
                        .boundsInRoot()
                        .relativeTo(tutorialRootBounds)
                }
            ) {
                SettingsRow(label = "개인정보 처리방침", onClick = {})

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(label = "이용 약관", onClick = {})

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(label = "문의하기", onClick = {})
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "계정 관리") {
                SettingsRow(
                    label = "로그아웃",
                    labelColor = AppColor.textPrimary,
                    onClick = { showLogoutDialog = true }
                )

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    label = "회원탈퇴",
                    labelColor = AppColor.errorPrimary,
                    onClick = onDeleteAccount
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        val settingsHeaderHeight = (252.dp - 180.dp * p).coerceAtLeast(72.dp)

        VerticalScrollbar(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = settingsHeaderHeight)
        )

        CollapsingSettingsHeader(
            p = p,
            userName = userName,
            role = if (isGuardian) "보호자" else "사용자",
            weeklyScore = weeklyScore,
            streakDays = streakDays,
            guardianLabel = guardianLabel,
            isGuardian = isGuardian,
            onBack = onBack,
            onProfileEdit = onProfileEdit
        )

        if (
            tutorialState.isRunning &&
            tutorialState.currentScreen == TutorialScreen.SETTINGS
        ) {
            val targetBounds = when (tutorialState.settingsStep) {
                SettingsTutorialStep.ACCESSIBILITY -> accessibilityBounds
                SettingsTutorialStep.LOCATION_SHARING -> locationSharingBounds
                SettingsTutorialStep.CHECK_NOTIFICATION -> notificationBounds
                SettingsTutorialStep.NOTIFICATION_TIME -> notificationTimeBounds
                SettingsTutorialStep.SUPPORT -> supportBounds
                SettingsTutorialStep.COMPLETED -> null
            }

            /*
             * 선택한 설정 항목의 위치가 측정된 뒤에만 오버레이를 표시합니다.
             * 위치 정보 공유 단계에서 targetBounds가 null인 채 스크림만 보이는
             * 현상을 방지합니다.
             */
            if (
                targetBounds != null ||
                tutorialState.settingsStep == SettingsTutorialStep.COMPLETED
            ) {
                SettingsTutorialOverlay(
                    step = tutorialState.settingsStep,
                    targetBounds = targetBounds,
                    currentNumber = tutorialState.currentNumber,
                    totalNumber = tutorialState.totalNumber,
                    onNext = {
                        when (tutorialState.settingsStep) {
                            SettingsTutorialStep.ACCESSIBILITY -> {
                                tutorialViewModel.moveSettingsStep(
                                    SettingsTutorialStep.LOCATION_SHARING,
                                    tutorialState.currentNumber + 1
                                )
                            }

                            SettingsTutorialStep.LOCATION_SHARING -> {
                                tutorialViewModel.moveSettingsStep(
                                    SettingsTutorialStep.CHECK_NOTIFICATION,
                                    tutorialState.currentNumber + 1
                                )
                            }

                            SettingsTutorialStep.CHECK_NOTIFICATION -> {
                                tutorialViewModel.moveSettingsStep(
                                    SettingsTutorialStep.NOTIFICATION_TIME,
                                    tutorialState.currentNumber + 1
                                )
                            }

                            SettingsTutorialStep.NOTIFICATION_TIME -> {
                                tutorialViewModel.moveSettingsStep(
                                    SettingsTutorialStep.SUPPORT,
                                    tutorialState.currentNumber + 1
                                )
                            }

                            SettingsTutorialStep.SUPPORT,
                            SettingsTutorialStep.COMPLETED -> {
                                notificationEnabled = savedNotificationEnabled
                                tutorialViewModel.completeTutorial()
                            }
                        }
                    },
                    onSkip = {
                        notificationEnabled = savedNotificationEnabled
                        tutorialViewModel.stopTutorial()
                    }
                )
            }
        }
    }
}


/**
 * boundsInRoot()로 측정한 대상 영역을 SettingsScreen 최상위 Box 기준으로 변환합니다.
 *
 * 루트가 아직 측정되지 않은 최초 프레임에는 null을 반환하고,
 * 이후 onGloballyPositioned가 다시 호출되면 정상 좌표가 저장됩니다.
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
private fun SettingsTutorialOverlay(
    step: SettingsTutorialStep,
    targetBounds: Rect?,
    currentNumber: Int,
    totalNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current

    /*
     * AppColor의 색상 값이 @Composable getter로 선언되어 있을 수 있으므로
     * Canvas DrawScope 내부에서 직접 호출하지 않고 Composable 영역에서 미리 읽습니다.
     */
    val highlightColor = AppColor.greenPrimary

    val title = when (step) {
        SettingsTutorialStep.ACCESSIBILITY -> "접근성 설정"
        SettingsTutorialStep.LOCATION_SHARING -> "위치 정보 공유"
        SettingsTutorialStep.CHECK_NOTIFICATION -> "점검 알림"
        SettingsTutorialStep.NOTIFICATION_TIME -> "알림 시간"
        SettingsTutorialStep.SUPPORT -> "지원 메뉴"
        SettingsTutorialStep.COMPLETED -> "설정 사용법 완료"
    }

    val description = when (step) {
        SettingsTutorialStep.ACCESSIBILITY ->
            "글자 크기와 화면 표시 방식을 편하게 조절할 수 있어요."

        SettingsTutorialStep.LOCATION_SHARING ->
            "이 기능을 켜면 보호자가 내 위치를 확인할 수 있어요."

        SettingsTutorialStep.CHECK_NOTIFICATION ->
            "매일 정해진 시간에 점검 알림을 받을 수 있어요."

        SettingsTutorialStep.NOTIFICATION_TIME ->
            "알림을 받고 싶은 시간을 직접 선택할 수 있어요."

        SettingsTutorialStep.SUPPORT ->
            "개인정보 처리방침, 이용 약관과 문의 메뉴를 확인할 수 있어요."

        SettingsTutorialStep.COMPLETED ->
            "설정 화면의 주요 기능을 모두 살펴봤어요."
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {})
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }

        /*
         * 강조 대상이 화면 아래쪽에 있으면 안내창을 위로,
         * 강조 대상이 화면 위쪽에 있으면 안내창을 아래로 배치합니다.
         */
        val showPopupAtTop = targetBounds?.let { bounds ->
            bounds.center.y > screenHeightPx * 0.52f
        } ?: false

        val popupAlignment = if (showPopupAtTop) {
            Alignment.TopCenter
        } else {
            Alignment.BottomCenter
        }

        /*
         * 화면 전체를 어둡게 한 뒤 강조 대상 영역만 투명하게 뚫습니다.
         * 따라서 강조 영역 내부는 원래 밝기로 보이고 바깥쪽만 어두워집니다.
         */
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
                val padding = 4.dp.toPx()
                val left = (bounds.left - padding).coerceAtLeast(0f)
                val top = (bounds.top - padding).coerceAtLeast(0f)
                val right = (bounds.right + padding).coerceAtMost(size.width)
                val bottom = (bounds.bottom + padding).coerceAtMost(size.height)

                val highlightTopLeft = Offset(left, top)
                val highlightSize = Size(
                    width = (right - left).coerceAtLeast(0f),
                    height = (bottom - top).coerceAtLeast(0f)
                )
                val cornerRadius = CornerRadius(
                    x = 18.dp.toPx(),
                    y = 18.dp.toPx()
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
                    color = AppColor.textSecondary
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
                                step == SettingsTutorialStep.SUPPORT ||
                                step == SettingsTutorialStep.COMPLETED
                            ) {
                                "완료"
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
private fun TutorialSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (TutorialReplayTarget) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "어떤 사용법을 볼까요?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                TutorialSelectionRow(
                    title = "전체 사용법",
                    description = "처음부터 모든 기능을 차례대로 알아봐요",
                    onClick = {
                        onSelect(TutorialReplayTarget.FULL)
                    }
                )

                TutorialSelectionDivider()

                TutorialSelectionRow(
                    title = "홈 화면",
                    description = "주간 점검과 주요 메뉴 사용법을 알아봐요",
                    onClick = {
                        onSelect(TutorialReplayTarget.HOME)
                    }
                )

                TutorialSelectionDivider()

                TutorialSelectionRow(
                    title = "음성 대화",
                    description = "대화 시작, 마이크, 종료 방법을 알아봐요",
                    onClick = {
                        onSelect(TutorialReplayTarget.VOICE_CHAT)
                    }
                )

                TutorialSelectionDivider()

                TutorialSelectionRow(
                    title = "분석 결과",
                    description = "인지 점수와 분석 결과 확인 방법을 알아봐요",
                    onClick = {
                        onSelect(TutorialReplayTarget.ANALYSIS)
                    }
                )

                TutorialSelectionDivider()

                TutorialSelectionRow(
                    title = "설정",
                    description = "접근성, 위치 공유, 알림 설정을 알아봐요",
                    onClick = {
                        onSelect(TutorialReplayTarget.SETTINGS)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "취소",
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        containerColor = BrandWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun TutorialSelectionRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColor.textTertiary
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColor.textTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TutorialSelectionDivider() {
    HorizontalDivider(
        color = AppColor.divider,
        thickness = 1.dp
    )
}

@Composable
private fun WheelTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelNumberPicker(
                    value = selectedHour,
                    range = 0..23,
                    displayedValues = (0..23).map { it.toString().padStart(2, '0') },
                    onValueChange = { selectedHour = it }
                )

                Text(
                    text = "시",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                WheelNumberPicker(
                    value = selectedMinute,
                    range = 0..59,
                    displayedValues = (0..59).map { it.toString().padStart(2, '0') },
                    onValueChange = { selectedMinute = it }
                )

                Text(
                    text = "분",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedHour, selectedMinute)
                }
            ) {
                Text(
                    text = "확인",
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "취소",
                    color = AppColor.textTertiary
                )
            }
        },
        containerColor = BrandWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    displayedValues: List<String>,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .width(80.dp)
            .height(150.dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                wrapSelectorWheel = true
                this.displayedValues = displayedValues.toTypedArray()
                this.value = value
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
            }
        },
        update = { picker ->
            picker.minValue = range.first
            picker.maxValue = range.last
            picker.displayedValues = displayedValues.toTypedArray()

            if (picker.value != value) {
                picker.value = value
            }
        }
    )
}

@Composable
private fun CollapsingSettingsHeader(
    p: Float,
    userName: String,
    role: String,
    weeklyScore: Int,
    streakDays: Int,
    guardianLabel: String,
    isGuardian: Boolean = false,
    onBack: () -> Unit,
    onProfileEdit: () -> Unit
) {
    val maxH = 252.dp
    val minH = 72.dp
    val range = maxH - minH
    val headerHeight = (maxH - range * p).coerceAtLeast(minH)

    val pe = (p / 0.55f).coerceIn(0f, 1f)
    val pm = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isGuardian) {
                        listOf(AppColor.guardianDark, AppColor.guardianPrimary)
                    } else {
                        listOf(AppColor.accentDark, AppColor.greenPrimary)
                    }
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = BrandWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "설정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            Surface(
                shape = CircleShape,
                color = BrandWhite,
                shadowElevation = AppColor.cardShadowElevation,
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = (8f * (1f - pm)).dp)
                    .alpha(pm)
                    .then(if (pm > 0f) Modifier.clickable(onClick = onProfileEdit) else Modifier)
            ) {
                Image(
                    painter = painterResource(R.drawable.char1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                .offset(y = (-16f * pe).dp)
                .alpha((1f - pe).coerceAtLeast(0f))
                .then(if (pe < 1f) Modifier.clickable(onClick = onProfileEdit) else Modifier),
            shape = RoundedCornerShape(20.dp),
            color = BrandWhite.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, BrandWhite.copy(alpha = 0.28f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BrandWhite,
                        shadowElevation = AppColor.cardShadowElevation,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.char1),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandWhite
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BrandWhite.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = role,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColor.accentDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = BrandWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = BrandWhite.copy(alpha = 0.22f),
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        value = if (weeklyScore > 0) "${weeklyScore}점" else "-",
                        label = "오늘 점수",
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(BrandWhite.copy(alpha = 0.22f))
                    )

                    StatItem(
                        value = "🔥 ${streakDays}일",
                        label = "연속 점검",
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(BrandWhite.copy(alpha = 0.22f))
                    )

                    StatItem(
                        value = guardianLabel,
                        label = "연결 보호자",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = BrandWhite
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BrandWhite.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BrandWhite,
            shadowElevation = AppColor.cardShadowElevation,
            border = AppColor.cardBorder
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    trailingText: String? = null,
    labelColor: Color = AppColor.textPrimary,
    showArrow: Boolean = trailingText == null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            fontWeight = FontWeight.Medium
        )

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary
            )
        } else if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColor.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColor.textTertiary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandWhite,
                checkedTrackColor = AppColor.accent,
                uncheckedThumbColor = BrandWhite,
                uncheckedTrackColor = AppColor.divider
            )
        )
    }
}

@Composable
private fun NotifTimeRow(
    title: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AppColor.greenSurface
            ) {
                Text(
                    text = formatNotifTime(hour, minute),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColor.accentDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColor.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    isDestructive: Boolean = false,
    isGuardian: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = when {
                        isDestructive -> AppColor.errorPrimary
                        isGuardian    -> AppColor.guardianPrimary
                        else          -> AppColor.greenPrimary
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소", color = AppColor.textTertiary)
            }
        },
        containerColor = BrandWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatNotifTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "오전" else "오후"

    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    return "$period $h:${minute.toString().padStart(2, '0')}"
}

private fun startLocationService(context: Context) {
    val intent = Intent(
        context,
        LocationForegroundService::class.java
    )

    ContextCompat.startForegroundService(
        context,
        intent
    )
}