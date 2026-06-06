package com.midas26.mobileapp.ui.settings

import android.Manifest
import android.os.Build
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.R
import com.midas26.mobileapp.notification.AlarmScheduler
import com.midas26.mobileapp.notification.NotificationHelper
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun GuardianSettingsScreen(
    userName: String = "홍길동",
    weeklyScore: Int = 75,
    streakDays: Int = 4,
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onAccessibility: () -> Unit = {},
    onManagedUsers: () -> Unit = {},
    onProfileEdit: () -> Unit = {},
    profileViewModel: ProfileEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val protectors by profileViewModel.protectors.collectAsState()

    val guardianLabel = when {
        protectors.isEmpty() -> "없음"
        protectors.size == 1 -> protectors[0].name ?: "보호자"
        else -> "${protectors[0].name ?: "보호자"} +${protectors.size - 1}"
    }

    var analysisAlertEnabled by remember { mutableStateOf(false) }
    var analysisHour by remember { mutableIntStateOf(9) }
    var analysisMinute by remember { mutableIntStateOf(0) }

    var showAnalysisTimePicker by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    NotificationHelper.createChannel(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            analysisAlertEnabled = false
            Toast.makeText(context, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showAnalysisTimePicker) {
        WheelTimePickerDialog(
            title = "분석 알림 시간",
            initialHour = analysisHour,
            initialMinute = analysisMinute,
            onDismiss = { showAnalysisTimePicker = false },
            onConfirm = { hour, minute ->
                analysisHour = hour
                analysisMinute = minute
                AlarmScheduler.schedule(context, hour, minute)
                showAnalysisTimePicker = false
            }
        )
    }

    val scrollState = rememberScrollState()
    val rangePx = with(LocalDensity.current) { 180.dp.toPx() }
    val p = (scrollState.value / rangePx).coerceIn(0f, 1f)

    if (showLogoutDialog) {
        ConfirmDialog(
            title = "로그아웃",
            message = "정말 로그아웃 하시겠어요?",
            confirmText = "로그아웃",
            onConfirm = {
                PrefsManager.from(context).clearToken()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(252.dp))
            Spacer(modifier = Modifier.height(10.dp))

            SettingsSection(title = "접근성") {
                SettingsRow(label = "접근성 설정", onClick = onAccessibility)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "사용자 설정") {
                SettingsRow(
                    label = "관리 중인 사용자",
                    onClick = onManagedUsers
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "앱 설정") {
                SettingsToggleRow(
                    label = "분석 알림",
                    description = "인지 분석 결과 관련 알림을 받아요",
                    checked = analysisAlertEnabled,
                    onCheckedChange = { enabled ->
                        analysisAlertEnabled = enabled

                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }

                            AlarmScheduler.schedule(context, analysisHour, analysisMinute)
                        } else {
                            AlarmScheduler.cancel(context)
                        }
                    }
                )

                if (analysisAlertEnabled) {
                    HorizontalDivider(
                        color = AppColor.divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    NotifTimeRow(
                        title = "분석 알림 시간",
                        hour = analysisHour,
                        minute = analysisMinute,
                        onClick = { showAnalysisTimePicker = true }
                    )
                }

                HorizontalDivider(
                    color = AppColor.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(label = "앱 버전", trailingText = "1.0.0", onClick = null)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "지원") {
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

        CollapsingGuardianSettingsHeader(
            p = p,
            userName = userName,
            role = "보호자",
            weeklyScore = weeklyScore,
            streakDays = streakDays,
            guardianLabel = guardianLabel,
            onBack = onBack,
            onProfileEdit = onProfileEdit
        )
    }
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
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text(
                    text = "확인",
                    color = AppColor.guardianDark,
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
private fun CollapsingGuardianSettingsHeader(
    p: Float,
    userName: String,
    role: String,
    weeklyScore: Int,
    streakDays: Int,
    guardianLabel: String,
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
                    colors = listOf(AppColor.guardianDark, AppColor.guardianPrimary)
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
                                color = AppColor.guardianDark,
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
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
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
                    color = if (isDestructive) AppColor.errorPrimary else AppColor.greenPrimary,
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