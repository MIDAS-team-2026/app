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
    todayIndex: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(brush = Brush.verticalGradient(
                colors = listOf(Green600, Green400)
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
                streakDays = streakDays,
                weeklyChecks = weeklyChecks,
                todayIndex = todayIndex
            )
        }
    }
}

@Composable
private fun WeekStatusCard(
    streakDays: Int,
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
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Gray400,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Gray200)
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
            accent = Gray100,
            iconTint = Gray400,
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
    accent: Color = Green50,
    iconTint: Color = Green600
) {
    Surface(
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
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
