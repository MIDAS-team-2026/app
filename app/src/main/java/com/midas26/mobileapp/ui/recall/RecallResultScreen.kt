package com.midas26.mobileapp.ui.recall

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Amber400
import com.midas26.mobileapp.ui.theme.Amber50
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun RecallResultScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onSeeDetails: () -> Unit = {},
    viewModel: RecallViewModel = viewModel()
) {
    val score = viewModel.computeResult()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
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
                    tint = Gray800,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "회상 결과",
                style = MaterialTheme.typography.titleLarge,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // 점수 원
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(132.dp),
                    shape = CircleShape,
                    color = Green50,
                    border = androidx.compose.foundation.BorderStroke(3.dp, Green400)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = score.total.toString(),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green600
                            )
                            Text(
                                text = "일치도 %",
                                style = MaterialTheme.typography.bodySmall,
                                color = Green600,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // 정상 범위 칩
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = Green50
                ) {
                    Text(
                        text = if (score.isNormal) "✓ 정상 범위" else "주의 필요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green600,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 헤딩
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (score.isNormal) "기억력이 잘 유지되고 있어요!" else "기억력 점검을 자주 해보세요",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Gray800,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 상세 분석
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Gray200)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "상세 분석",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray400,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    score.items.forEachIndexed { idx, item ->
                        DetailRow(item = item)
                        if (idx < score.items.lastIndex) Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // 노트
            if (score.note != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Amber50
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(text = "💬", fontSize = 22.sp)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = score.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gray800
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 하단 버튼 두 개
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(onClick = onGoHome),
                    shape = RoundedCornerShape(16.dp),
                    color = BrandWhite,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Gray200)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "홈으로",
                            style = MaterialTheme.typography.labelLarge,
                            color = Green600,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppPrimaryButton(text = "상세 분석", onClick = onSeeDetails)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailRow(item: RecallScore.DetailItem) {
    val barColor = if (item.isWarning) Amber400 else Green400
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Gray800,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${item.percent}%",
                style = MaterialTheme.typography.titleMedium,
                color = barColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Gray100)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.percent / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}
