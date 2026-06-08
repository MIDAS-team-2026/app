package com.midas26.mobileapp.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun UserHomeScreen(
    userName: String = "홍길동",
    streakDays: Int = 4,
    weeklyChecks: List<Boolean> = List(7) { false },
    weeklyDayLabels: List<String> = listOf("일", "월", "화", "수", "목", "금", "토"),
    todayIndex: Int = 6,
    onMenuClick: (UserMenu) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UserHomeHeader(
            userName        = userName,
            streakDays      = streakDays,
            weeklyChecks    = weeklyChecks,
            weeklyDayLabels = weeklyDayLabels,
            todayIndex      = todayIndex
        )
        Spacer(modifier = Modifier.height(12.dp))

        MenuGrid(
            onMenuClick = onMenuClick,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun UserHomeHeader(
    userName: String,
    streakDays: Int,
    weeklyChecks: List<Boolean>,
    weeklyDayLabels: List<String>,
    todayIndex: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(brush = Brush.verticalGradient(
                colors = listOf(AppColor.accentDark, AppColor.greenPrimary)
            ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 20.dp)
        ) {
            Text(
                text = "$userName ${stringResource(R.string.home_user_suffix)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
            Spacer(modifier = Modifier.height(12.dp))
            WeekStatusCard(
                streakDays      = streakDays,
                weeklyChecks    = weeklyChecks,
                weeklyDayLabels = weeklyDayLabels,
                todayIndex      = todayIndex
            )
        }
    }
}

@Composable
private fun WeekStatusCard(
    streakDays: Int,
    weeklyChecks: List<Boolean>,
    weeklyDayLabels: List<String>,
    todayIndex: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, BrandWhite.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.home_streak, streakDays),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyDayLabels.forEachIndexed { idx, label ->
                    DayStatusDot(
                        dayLabel = label,
                        checked  = weeklyChecks.getOrNull(idx) == true,
                        isToday  = idx == todayIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun DayStatusDot(dayLabel: String, checked: Boolean, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = BrandWhite.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            modifier = Modifier
                .size(36.dp)
                .then(if (isToday) Modifier.border(2.dp, BrandWhite, CircleShape) else Modifier),
            shape = CircleShape,
            color = if (isToday) Color.Transparent
            else if (checked) BrandWhite.copy(alpha = 0.4f)
            else BrandWhite.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Text(text = "✓", color = BrandWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
private fun MenuGrid(
    onMenuClick: (UserMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        SectionHeader(title = stringResource(R.string.home_today_check))
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            icon = Icons.Default.Mic,
            titleRes = R.string.menu_voice_chat,
            descRes = R.string.menu_voice_chat_desc,
            onClick = { onMenuClick(UserMenu.VoiceChat) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            icon = Icons.Default.BarChart,
            titleRes = R.string.menu_analysis,
            descRes = R.string.menu_analysis_desc,
            onClick = { onMenuClick(UserMenu.Analysis) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(40.dp))
        SectionHeader(title = stringResource(R.string.section_settings))
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            icon = Icons.Default.Settings,
            titleRes = R.string.menu_settings,
            descRes = R.string.menu_settings_desc,
            onClick = { onMenuClick(UserMenu.Settings) },
            accent = AppColor.surfaceElevated,
            iconTint = AppColor.textTertiary,
            modifier = Modifier.fillMaxWidth()
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
            Column(modifier = Modifier.weight(1f)) {
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
                Box(contentAlignment = Alignment.Center) {
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

// === 데이터 모델 ===

enum class UserMenu { VoiceChat, Recall, Analysis, Settings }
