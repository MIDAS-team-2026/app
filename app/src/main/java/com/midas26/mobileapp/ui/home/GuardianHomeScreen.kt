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
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.GuardianAccent
import com.midas26.mobileapp.ui.theme.GuardianAccentDark
import com.midas26.mobileapp.ui.theme.GuardianAccentLight
import com.midas26.mobileapp.ui.theme.BrandWhite

enum class GuardianMenu { Analysis, Location, Info, Settings }

@Composable
fun GuardianHomeScreen(
    guardianName: String = "홍철수",
    linkedUserName: String = "홍길동",
    weeklyScore: Int = 75,
    voiceCheckDone: Boolean = true,
    recallCheckDone: Boolean = false,
    onMenuClick: (GuardianMenu) -> Unit = {},
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
                .padding(bottom = 72.dp)
        ) {
            GuardianHeader(
                guardianName = guardianName,
                linkedUserName = linkedUserName,
                voiceCheckDone = voiceCheckDone,
                recallCheckDone = recallCheckDone
            )
            Spacer(modifier = Modifier.height(16.dp))

            GuardianScoreCard(score = weeklyScore)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.guardian_menu),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuardianMenuGrid(
                onMenuClick = onMenuClick,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        BottomTabBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            tabs = guardianTabs,
            selectedTab = GuardianHomeTab.Home,
            onTabClick = onTabClick,
            accent = GuardianAccentDark
        )
    }
}

@Composable
private fun GuardianHeader(
    guardianName: String,
    linkedUserName: String,
    voiceCheckDone: Boolean,
    recallCheckDone: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(GuardianAccent)
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
                text = "$guardianName ${stringResource(R.string.guardian_user_suffix)}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite.copy(alpha = 0.20f)
            ) {
                Text(
                    text = stringResource(R.string.guardian_linked, linkedUserName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandWhite,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            TodayStatusCard(
                voiceCheckDone = voiceCheckDone,
                recallCheckDone = recallCheckDone
            )
        }
    }
}

@Composable
private fun TodayStatusCard(voiceCheckDone: Boolean, recallCheckDone: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite.copy(alpha = 0.20f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.guardian_today_status),
                style = MaterialTheme.typography.bodySmall,
                color = BrandWhite.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusItem(label = stringResource(R.string.guardian_voice_done), done = voiceCheckDone)
                StatusItem(label = stringResource(R.string.guardian_recall_pending), done = recallCheckDone)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = BrandWhite.copy(alpha = if (done) 0.6f else 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (done) "✓" else "—",
                    color = BrandWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BrandWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GuardianScoreCard(score: Int) {
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
                color = GuardianAccentLight
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = score.toString(),
                            fontSize = 28.sp,
                            color = GuardianAccentDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.home_score_unit),
                            fontSize = 14.sp,
                            color = GuardianAccent
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
                        color = GuardianAccentLight
                    ) {
                        Text(
                            text = stringResource(R.string.home_score_normal),
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianAccentDark,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.guardian_score_normal),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }
    }
}

@Composable
private fun GuardianMenuGrid(
    onMenuClick: (GuardianMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GuardianMenuCard(
                emoji = "📊",
                titleRes = R.string.guardian_menu_analysis,
                onClick = { onMenuClick(GuardianMenu.Analysis) },
                modifier = Modifier.weight(1f)
            )
            GuardianMenuCard(
                emoji = "📍",
                titleRes = R.string.guardian_menu_location,
                onClick = { onMenuClick(GuardianMenu.Location) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GuardianMenuCard(
                emoji = "📋",
                titleRes = R.string.guardian_menu_info,
                onClick = { onMenuClick(GuardianMenu.Info) },
                modifier = Modifier.weight(1f)
            )
            GuardianMenuCard(
                emoji = "⚙️",
                titleRes = R.string.menu_settings,
                onClick = { onMenuClick(GuardianMenu.Settings) },
                accent = Gray100,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GuardianMenuCard(
    emoji: String,
    titleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = GuardianAccentLight
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
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
    }
}

private val guardianTabs = listOf(
    TabItem(GuardianHomeTab.Home,     "🏠", R.string.tab_home),
    TabItem(GuardianHomeTab.Analysis, "📊", R.string.tab_analysis),
    TabItem(GuardianHomeTab.Location, "📍", R.string.guardian_tab_location),
    TabItem(GuardianHomeTab.Settings, "⚙️", R.string.menu_settings)
)
