package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    onShowGraph: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    var showShareSheet by remember { mutableStateOf(false) }
    if (showShareSheet) {
        ShareToGuardianSheet(
            onDismiss = { showShareSheet = false },
            onShare = { showShareSheet = false /* TODO: 백엔드 공유 API 호출 */ },
            viewModel = viewModel
        )
    }
    val onShare: () -> Unit = { showShareSheet = true }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // 상단 녹색 헤더 (260dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Green400)
        ) {
            // 장식 원
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = 240.dp, y = 20.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.15f))
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .offset(x = (-30).dp, y = 130.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.10f))
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // 앱바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        text = "분석 결과",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "보호자에게 공유",
                            tint = BrandWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = viewModel.todayDateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = viewModel.todayScore.toString(),
                            fontSize = 56.sp,
                            color = BrandWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "점",
                            fontSize = 18.sp,
                            color = BrandWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = BrandWhite,
                            modifier = Modifier.padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = viewModel.scoreDeltaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Green600,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = viewModel.scoreDeltaSubtext,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )
                }
            }
        }

        // 본문 (4 항목 카드 2x2 + 코멘트 + 그래프 보기 버튼)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            // 4 항목 (2x2)
            val items = viewModel.todayItems
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemCard(item = items[0], modifier = Modifier.weight(1f))
                ItemCard(item = items[1], modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemCard(item = items[2], modifier = Modifier.weight(1f))
                ItemCard(item = items[3], modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 코멘트 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Green50
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        tint = Green600,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "오늘의 코멘트",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Green600,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = viewModel.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gray800
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AppPrimaryButton(text = "그래프로 보기", leadingIcon = Icons.AutoMirrored.Filled.TrendingUp, onClick = onShowGraph)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ItemCard(item: AnalysisItem, modifier: Modifier = Modifier) {
    val accent = when (item.trend) {
        AnalysisItem.Trend.Up     -> Green600
        AnalysisItem.Trend.Down   -> Green400
        AnalysisItem.Trend.Steady -> Gray400
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gray200)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Gray400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.valueText,
                fontSize = 28.sp,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.trendText,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
