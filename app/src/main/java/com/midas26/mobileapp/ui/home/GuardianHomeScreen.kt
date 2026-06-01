package com.midas26.mobileapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.midas26.mobileapp.ui.theme.*

enum class GuardianMenu { Analysis, Location, Info, Settings }

// ══════════════════════════════════════════════════════════════════════════════
// 보호자 홈 화면
// - 하단 네비게이션 바는 AppNavHost Scaffold 에서 공통 처리
// - 위치 정보 카드 클릭 → onMenuClick(GuardianMenu.Location)
//   → AppNavHost 에서 Routes.LocationList 로 이동
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun GuardianHomeScreen(
    guardianName: String     = "홍철수",
    linkedUserName: String   = "홍길동",
    weeklyScore: Int         = 75,
    voiceCheckDone: Boolean  = true,
    recallCheckDone: Boolean = false,
    onMenuClick: (GuardianMenu) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GuardianHeader(
            guardianName    = guardianName,
            linkedUserName  = linkedUserName,
            voiceCheckDone  = voiceCheckDone,
            recallCheckDone = recallCheckDone
        )
        Spacer(modifier = Modifier.height(16.dp))
        GuardianScoreCard(score = weeklyScore)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.guardian_menu),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        GuardianMenuGrid(
            onMenuClick = onMenuClick,
            modifier    = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.height(12.dp))
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
        modifier = Modifier.fillMaxWidth().height(240.dp).background(GuardianAccent)
    ) {
        Box(modifier = Modifier.size(200.dp).offset(x = 240.dp, y = 20.dp)
            .clip(CircleShape).background(BrandWhite.copy(alpha = 0.15f)))
        Box(modifier = Modifier.size(100.dp).offset(x = (-30).dp, y = 110.dp)
            .clip(CircleShape).background(BrandWhite.copy(alpha = 0.10f)))
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 16.dp)
        ) {
            Text(stringResource(R.string.home_hello),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandWhite.copy(alpha = 0.9f))
            Spacer(modifier = Modifier.height(2.dp))
            Text("$guardianName ${stringResource(R.string.guardian_user_suffix)}",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BrandWhite)
            Spacer(modifier = Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = BrandWhite.copy(alpha = 0.20f)) {
                Text(stringResource(R.string.guardian_linked, linkedUserName),
                    style = MaterialTheme.typography.bodyMedium, color = BrandWhite,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            TodayStatusCard(voiceCheckDone = voiceCheckDone, recallCheckDone = recallCheckDone)
        }
    }
}

@Composable
private fun TodayStatusCard(voiceCheckDone: Boolean, recallCheckDone: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = BrandWhite.copy(alpha = 0.20f)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.guardian_today_status),
                style = MaterialTheme.typography.bodySmall, color = BrandWhite.copy(alpha = 0.9f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                StatusItem(stringResource(R.string.guardian_voice_done),     voiceCheckDone)
                StatusItem(stringResource(R.string.guardian_recall_pending), recallCheckDone)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(22.dp), shape = CircleShape,
            color = BrandWhite.copy(alpha = if (done) 0.6f else 0.2f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (done) "✓" else "—", color = BrandWhite,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = BrandWhite, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GuardianScoreCard(score: Int) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp), color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation, border = AppColor.cardBorder) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = GuardianAccentLight) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(score.toString(), fontSize = 28.sp,
                            color = GuardianAccentDark, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.home_score_unit),
                            fontSize = 14.sp, color = GuardianAccent)
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_week_score_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColor.textPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(11.dp), color = GuardianAccentLight) {
                        Text(stringResource(R.string.home_score_normal),
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianAccentDark, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.guardian_score_normal),
                    style = MaterialTheme.typography.bodySmall, color = AppColor.textTertiary)
            }
        }
    }
}

@Composable
private fun GuardianMenuGrid(
    onMenuClick: (GuardianMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GuardianMenuCard(Icons.Default.BarChart, R.string.guardian_menu_analysis,
                { onMenuClick(GuardianMenu.Analysis) }, Modifier.weight(1f))
            // ← 위치 정보 카드 클릭 → LocationList(화면1)로 이동
            GuardianMenuCard(Icons.Default.LocationOn, R.string.guardian_menu_location,
                { onMenuClick(GuardianMenu.Location) }, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GuardianMenuCard(Icons.AutoMirrored.Filled.Assignment, R.string.guardian_menu_info,
                { onMenuClick(GuardianMenu.Info) }, Modifier.weight(1f))
            GuardianMenuCard(Icons.Default.Settings, R.string.menu_settings,
                { onMenuClick(GuardianMenu.Settings) }, Modifier.weight(1f),
                accent = Gray100, iconTint = Gray400)
        }
    }
}

@Composable
private fun GuardianMenuCard(
    icon: ImageVector,
    titleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color   = GuardianAccentLight,
    iconTint: Color = GuardianAccentDark
) {
    Surface(modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation, border = AppColor.cardBorder) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(72.dp), shape = RoundedCornerShape(18.dp), color = accent) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint,
                        modifier = Modifier.size(36.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium,
                color = AppColor.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}