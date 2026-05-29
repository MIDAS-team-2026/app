package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.midas26.mobileapp.location.LocationForegroundService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Brush
import com.midas26.mobileapp.ui.theme.Green500
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray600
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Red400
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.R
import com.midas26.mobileapp.notification.AlarmScheduler
import com.midas26.mobileapp.notification.NotificationHelper
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SettingsScreen(
    userName: String = "홍길동",
    weeklyScore: Int = 75,
    streakDays: Int = 4,
    guardianName: String = "홍철수",
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onAccessibility: () -> Unit = {},
    onProfileEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)
    val role = prefs.getUserRole()
    val isGuardian = role == PrefsManager.ROLE_GUARDIAN
    var locationSharingEnabled by remember { mutableStateOf(prefs.getLocationSharingEnabled()) }
    var notificationEnabled by remember { mutableStateOf(prefs.getNotificationEnabled()) }
    var notifHour by remember { mutableIntStateOf(prefs.getNotificationHour()) }
    var notifMinute by remember { mutableIntStateOf(prefs.getNotificationMinute()) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    NotificationHelper.createChannel(context)

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs.setLocationSharingEnabled(true)
            context.startForegroundService(Intent(context, LocationForegroundService::class.java))
        } else {
            locationSharingEnabled = false
            Toast.makeText(context, "항상 허용 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
        // ── 스크롤 콘텐츠 ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 헤더 펼침 높이만큼 상단 공간 확보
            Spacer(modifier = Modifier.height(252.dp))
            Spacer(modifier = Modifier.height(10.dp))

            SettingsSection(title = "접근성") {
                SettingsRow(label = "접근성 설정", onClick = onAccessibility)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "앱 설정") {
                if (!isGuardian) {
                    SettingsToggleRow(
                        label = "보호자에게 위치 정보 제공",
                        description = "보호자가 내 위치를 확인할 수 있어요",
                        checked = locationSharingEnabled,
                        onCheckedChange = { enabled ->
                            locationSharingEnabled = enabled
                            if (enabled) {
                                val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (bgGranted) {
                                    prefs.setLocationSharingEnabled(true)
                                    context.startForegroundService(
                                        Intent(context, LocationForegroundService::class.java)
                                    )
                                } else {
                                    backgroundLocationLauncher.launch(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    )
                                }
                            } else {
                                prefs.setLocationSharingEnabled(false)
                                context.stopService(Intent(context, LocationForegroundService::class.java))
                            }
                        }
                    )
                    HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                SettingsToggleRow(
                    label = "점검 알림",
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
                        HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        NotifTimeRow(
                            hour = notifHour,
                            minute = notifMinute,
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        notifHour = hour
                                        notifMinute = minute
                                        prefs.setNotificationTime(hour, minute)
                                        AlarmScheduler.schedule(context, hour, minute)
                                    },
                                    notifHour,
                                    notifMinute,
                                    false
                                ).show()
                            }
                        )
                    }
                }
                HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(label = "앱 버전", trailingText = "1.0.0", onClick = null)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "지원") {
                SettingsRow(label = "개인정보 처리방침", onClick = {})
                HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(label = "이용 약관", onClick = {})
                HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(label = "문의하기", onClick = {})
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "계정 관리") {
                SettingsRow(
                    label = "로그아웃",
                    labelColor = Gray800,
                    onClick = { showLogoutDialog = true }
                )
                HorizontalDivider(color = AppColor.divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    label = "회원탈퇴",
                    labelColor = Red400,
                    onClick = onDeleteAccount
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // ── 스크롤바 오버레이 (헤더 접힘 모션과 동기화) ─────────────
        val settingsHeaderHeight = (252.dp - 180.dp * p).coerceAtLeast(72.dp)
        VerticalScrollbar(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = settingsHeaderHeight)
        )

        // ── 접히는 헤더 (스크롤 위에 오버레이) ──────────────────────
        CollapsingSettingsHeader(
            p = p,
            userName = userName,
            role = if (isGuardian) "보호자" else "사용자",
            weeklyScore = weeklyScore,
            streakDays = streakDays,
            guardianName = guardianName,
            onBack = onBack,
            onProfileEdit = onProfileEdit
        )
    }
}

@Composable
private fun CollapsingSettingsHeader(
    p: Float,
    userName: String,
    role: String,
    weeklyScore: Int,
    streakDays: Int,
    guardianName: String,
    onBack: () -> Unit,
    onProfileEdit: () -> Unit
) {
    val maxH = 252.dp
    val minH = 72.dp
    val range = maxH - minH
    val headerHeight = (maxH - range * p).coerceAtLeast(minH)

    // pe: 프로필 카드 페이드아웃 (p=0→0.55에서 완료)
    val pe = (p / 0.55f).coerceIn(0f, 1f)
    // pm: 미니 아바타 페이드인 (p=0.5→1.0)
    val pm = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(brush = Brush.verticalGradient(colors = listOf(Green600, Green400)))
    ) {
        // ── 네비 행 (항상 상단 고정) ──────────────────────────────
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
            // 미니 아바타 (접힘 상태에서만 등장)
            Surface(
                shape = CircleShape,
                color = BrandWhite,
                shadowElevation = 4.dp,
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

        // ── 펼침 프로필 카드 (스크롤 시 페이드아웃) ──────────────
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
                // 상단 행: 아바타 + 이름 + 역할 배지 + 화살표
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BrandWhite,
                        shadowElevation = 3.dp,
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
                                color = Green600,
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
                // 구분선
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = BrandWhite.copy(alpha = 0.22f),
                    thickness = 1.dp
                )
                // 통계 행
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(value = "${weeklyScore}점", label = "이번 주", modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(BrandWhite.copy(alpha = 0.22f)))
                    StatItem(value = "🔥 ${streakDays}일", label = "연속 점검", modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(BrandWhite.copy(alpha = 0.22f)))
                    StatItem(value = guardianName, label = "연결 보호자", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BrandWhite)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = BrandWhite.copy(alpha = 0.85f))
    }
}

@Composable
private fun ProfileCard(
    userName: String,
    role: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = BrandWhite
            ) {
                Image(
                    painter = painterResource(R.drawable.char1),
                    contentDescription = "프로필 이미지",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Green50
                ) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.accentDark,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
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
            shadowElevation = 3.dp
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    trailingText: String? = null,
    labelColor: Color = Gray800,
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
                uncheckedTrackColor = Gray200
            )
        )
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
                    color = if (isDestructive) Red400 else Green400,
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

@Composable
private fun NotifTimeRow(
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
            text = "알림 시간",
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
                color = Green50
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

private fun formatNotifTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "오전" else "오후"
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$period $h:${minute.toString().padStart(2, '0')}"
}
