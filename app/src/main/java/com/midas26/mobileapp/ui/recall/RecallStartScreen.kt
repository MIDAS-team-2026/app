package com.midas26.mobileapp.ui.recall

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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Green900
import com.midas26.mobileapp.ui.theme.BrandWhite

/**
 * 회상 과제 시작 화면 (A2).
 * 상단 녹색 헤더에 큰 타이틀(흰색), 본문에 "오늘의 과제" 카드 + 소요 시간 노트.
 */
@Composable
fun RecallStartScreen(
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // 상단 녹색 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Green400)
        ) {
            // 장식 원
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .offset(x = 250.dp, y = 30.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.15f))
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = 180.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.10f))
            )

            // 앱바 + 타이틀
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(56.dp)
                        .padding(top = 8.dp),
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
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "회상 과제",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = BrandWhite,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "기억 점검을\n시작할까요?",
                        fontSize = 32.sp,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "이전 대화 내용을 바탕으로\n몇 가지 질문을 드릴게요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )
                }
            }
        }

        // 본문
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp)
        ) {
            // 오늘의 과제 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite,
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, com.midas26.mobileapp.ui.theme.Gray200)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "오늘의 과제",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray400,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TodayTaskRow(num = 1, label = "최근 대화 내용 회상")
                    Spacer(modifier = Modifier.height(10.dp))
                    TodayTaskRow(num = 2, label = "단어/장소 기억 점검")
                    Spacer(modifier = Modifier.height(10.dp))
                    TodayTaskRow(num = 3, label = "시간/날짜 인지 확인")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 소요 시간 노트
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Green50
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Green400,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "소요 시간 약 3-5분이에요",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Gray800,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "편한 자세로 진행하세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = Green400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AppPrimaryButton(text = "시작하기", onClick = onStart)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TodayTaskRow(num: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = Green600
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = num.toString(),
                    color = BrandWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Gray800,
            fontWeight = FontWeight.Medium
        )
    }
}
