package com.midas26.mobileapp.ui.home

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
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green100
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Red400
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun UserHomeScreen(
    userName: String = "홍길동",
    weeklyScore: Int = 75,
    streakDays: Int = 4,
    weeklyChecks: List<Boolean> = listOf(false, true, true, true, true, true, false), // 일~토
    todayIndex: Int = 6, // 토요일이 오늘 (0=일)
    onMenuClick: (UserMenu) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UserHomeHeader(
            userName = userName,
            streakDays = streakDays,
            weeklyChecks = weeklyChecks,
            todayIndex = todayIndex
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.home_today_check),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        MenuGrid(
            onMenuClick = onMenuClick,
            todayChecked = weeklyChecks.getOrNull(todayIndex) == true,
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
    todayIndex: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(brush = Brush.linearGradient(
                colors = listOf(Green500, Green400),
                start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
                end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
            ))
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_hello),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandWhite.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$userName ${stringResource(R.string.home_user_suffix)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite.copy(alpha = 0.20f)
            ) {
                Text(
                    text = stringResource(R.string.home_streak, streakDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandWhite,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            WeekStatusCard(weeklyChecks = weeklyChecks, todayIndex = todayIndex)
        }
    }
}

@Composable
private fun WeekStatusCard(
    weeklyChecks: List<Boolean>,
    todayIndex: Int
) {
    val weekdays = listOf(
        R.string.weekday_sun, R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed,
        R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite.copy(alpha = 0.20f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.home_week_status),
                style = MaterialTheme.typography.bodySmall,
                color = BrandWhite.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekdays.forEachIndexed { idx, dayRes ->
                    DayStatusDot(
                        dayLabel = stringResource(dayRes),
                        checked = weeklyChecks.getOrNull(idx) == true,
                        isToday = idx == todayIndex
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
private fun ScoreCard(score: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Green50
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = score.toString(),
                            fontSize = 28.sp,
                            color = AppColor.accentDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.home_score_unit),
                            fontSize = 14.sp,
                            color = Green500
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_week_score_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColor.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Green50
                    ) {
                        Text(
                            text = stringResource(R.string.home_score_normal),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColor.accentDark,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_score_diff),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }
        }
    }
}

@Composable
private fun MenuGrid(
    onMenuClick: (UserMenu) -> Unit,
    todayChecked: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 음성대화 — 회상과제 자리까지 가로 전체 차지
        MenuCard(
            icon = Icons.Default.Mic,
            titleRes = R.string.menu_voice_chat,
            badge = if (!todayChecked) "!" else null,
            onClick = { onMenuClick(UserMenu.VoiceChat) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuCard(
                icon = Icons.Default.BarChart,
                titleRes = R.string.menu_analysis,
                onClick = { onMenuClick(UserMenu.Analysis) },
                modifier = Modifier.weight(1f)
            )
            MenuCard(
                icon = Icons.Default.Settings,
                titleRes = R.string.menu_settings,
                onClick = { onMenuClick(UserMenu.Settings) },
                accent = Gray100,
                iconTint = Gray400,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    titleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = Green50,
    iconTint: Color = Green600
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd),
                    shape = CircleShape,
                    color = Red400
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// === 데이터 모델 ===

enum class UserMenu { VoiceChat, Recall, Analysis, Settings }
