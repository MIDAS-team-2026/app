package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.midas26.mobileapp.notification.AlarmScheduler
import com.midas26.mobileapp.notification.NotificationHelper
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SettingsScreen(
    userName: String = "홍길동",
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
    val userCode = prefs.getUserCode().ifEmpty { "842716" } // 백엔드 연동 전 임시 기본값

    var notificationEnabled by remember { mutableStateOf(prefs.getNotificationEnabled()) }
    var notifHour by remember { mutableIntStateOf(prefs.getNotificationHour()) }
    var notifMinute by remember { mutableIntStateOf(prefs.getNotificationMinute()) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    NotificationHelper.createChannel(context)

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

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "회원탈퇴",
            message = "탈퇴하면 모든 데이터가 삭제되며\n복구할 수 없어요. 정말 탈퇴하시겠어요?",
            confirmText = "탈퇴하기",
            isDestructive = true,
            onConfirm = {
                PrefsManager.from(context).clearToken()
                onDeleteAccount()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        SettingsTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ProfileCard(
                userName = userName,
                role = if (isGuardian) "보호자" else "사용자",
                userCode = userCode
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "계정") {
                SettingsRow(label = "프로필 편집", onClick = onProfileEdit)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "접근성") {
                SettingsRow(label = "접근성 설정", onClick = onAccessibility)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSection(title = "앱 설정") {
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
                    showArrow = false,
                    onClick = { showDeleteDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 12.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary
                )
            }
            Text(
                text = "설정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }
    }
}

@Composable
private fun ProfileCard(
    userName: String,
    role: String,
    userCode: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
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
                color = Green50
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userName.take(1),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.accentDark
                    )
                }
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

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable {
                        clipboardManager.setText(AnnotatedString(userCode))
                        Toast.makeText(context, "코드가 복사되었어요", Toast.LENGTH_SHORT).show()
                    },
                shape = RoundedCornerShape(12.dp),
                color = Gray100
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "사용자 코드",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textTertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = userCode,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textSecondary,
                        letterSpacing = 2.sp
                    )
                }
            }
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
            shadowElevation = 1.dp
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
