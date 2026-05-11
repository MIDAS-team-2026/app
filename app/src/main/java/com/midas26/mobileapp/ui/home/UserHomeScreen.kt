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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun UserHomeScreen(
    userName: String = "홍길동",
    weeklyScore: Int = 75,
    streakDays: Int = 4,
    weeklyChecks: List<Boolean> = listOf(true, true, true, true, true, false, false), // 월~일
    todayIndex: Int = 5, // 토요일이 오늘 (0=월)
    onMenuClick: (UserMenu) -> Unit = {},
    onTabClick: (TabId) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 한 화면 안에 모두 표시 (스크롤 없음 — 노년층 가독성 우선)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp) // 탭바 높이 확보
        ) {
            UserHomeHeader(
                userName = userName,
                streakDays = streakDays,
                weeklyChecks = weeklyChecks,
                todayIndex = todayIndex
            )
            Spacer(modifier = Modifier.height(16.dp))

            ScoreCard(score = weeklyScore)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.home_today_check),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 남은 공간을 메뉴 그리드가 채우도록 weight 사용
            MenuGrid(
                onMenuClick = onMenuClick,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        BottomTabBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            tabs = userTabs,
            selectedTab = UserHomeTab.Home,
            onTabClick = onTabClick,
            accent = Green500
        )
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
            .height(240.dp)
            .background(Green400)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 240.dp, y = 20.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = (-30).dp, y = 110.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = stringResource(R.string.home_streak),
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
        R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed,
        R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite.copy(alpha = 0.20f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier.size(width = 30.dp, height = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isToday) BrandWhite
            else if (checked) BrandWhite.copy(alpha = 0.4f)
            else BrandWhite.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isToday) {
                    Text(
                        text = "오늘",
                        style = MaterialTheme.typography.bodySmall,
                        color = Green600,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                } else if (checked) {
                    Text(text = "✓", color = BrandWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            color = Green600,
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
                        color = Gray800,
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
                            color = Green600,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_score_diff),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }
    }
}

@Composable
private fun MenuGrid(
    onMenuClick: (UserMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 음성대화 — 회상과제 자리까지 가로 전체 차지
        MenuCard(
            emoji = "🎙️",
            titleRes = R.string.menu_voice_chat,
            badge = "N",
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
                emoji = "📊",
                titleRes = R.string.menu_analysis,
                onClick = { onMenuClick(UserMenu.Analysis) },
                modifier = Modifier.weight(1f)
            )
            MenuCard(
                emoji = "⚙️",
                titleRes = R.string.menu_settings,
                onClick = { onMenuClick(UserMenu.Settings) },
                accent = Gray100,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MenuCard(
    emoji: String,
    titleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = Green50
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
                        Text(text = emoji, fontSize = 44.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = Gray800,
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

@Composable
internal fun BottomTabBar(
    modifier: Modifier = Modifier,
    tabs: List<TabItem>,
    selectedTab: TabId,
    onTabClick: (TabId) -> Unit,
    accent: Color
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = Gray200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabClick(tab.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(text = tab.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) accent else Gray400,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// === 데이터 모델 / 자리표시자 ===

enum class UserMenu { VoiceChat, Recall, Analysis, Settings }
sealed interface TabId
enum class UserHomeTab : TabId { Home, Chat, Analysis, Profile }
enum class GuardianHomeTab : TabId { Home, Analysis, Location, Settings }

internal data class TabItem(val id: TabId, val emoji: String, val labelRes: Int)

private val userTabs = listOf(
    TabItem(UserHomeTab.Home,     "🏠", R.string.tab_home),
    TabItem(UserHomeTab.Chat,     "🎙️", R.string.tab_chat),
    TabItem(UserHomeTab.Analysis, "📈", R.string.tab_analysis),
    TabItem(UserHomeTab.Profile,  "👤", R.string.tab_profile)
)
