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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray600
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Green900
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Red400
import com.midas26.mobileapp.ui.voicechat.RecordingTimer
import com.midas26.mobileapp.ui.voicechat.Waveform

/**
 * 회상 질문 + 답변 녹음 통합 화면 (A3, A4).
 * 같은 화면에서 [RecallState] 에 따라 상단/하단이 변합니다.
 */
@Composable
fun RecallQuestionScreen(
    onBack: () -> Unit,
    onFinishedAll: () -> Unit,
    viewModel: RecallViewModel = viewModel()
) {
    val q = viewModel.currentQuestion
    val state = viewModel.state
    val isRecording = state is RecallState.Recording

    // 마지막 문항 stop 후 결과 페이지 이동 신호 처리
    LaunchedEffect(viewModel.requestNavigateToResult) {
        if (viewModel.requestNavigateToResult) {
            onFinishedAll()
            viewModel.consumeResultNavigation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // 진행 바
        val progress = q.number.toFloat() / q.total
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Gray100)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Green400)
            )
        }

        // 앱바 (← + "회상 과제" + "n / total")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
                text = "회상 과제",
                style = MaterialTheme.typography.titleLarge,
                color = Gray800,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${q.number} / ${q.total}",
                style = MaterialTheme.typography.bodyLarge,
                color = Gray400,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // 본문 (질문 + 부제 + 힌트)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // 카테고리 칩
            Surface(
                shape = CircleShape,
                color = Green50
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = q.categoryIcon,
                        contentDescription = null,
                        tint = Green600,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        text = q.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = Green600,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 질문
            Text(
                text = q.text,
                fontSize = if (isRecording) 22.sp else 30.sp,
                color = if (isRecording) Gray600 else Gray800,
                fontWeight = if (isRecording) FontWeight.Medium else FontWeight.Bold,
                lineHeight = if (isRecording) 30.sp else 40.sp
            )

            if (!isRecording) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "기억나는 대로 천천히 말씀해주세요.\n정확하지 않아도 괜찮아요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400
                )
                if (q.hint != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HintCard(hint = q.hint)
                }
            } else {
                Spacer(modifier = Modifier.height(28.dp))
                // 큰 음파 (녹음 중)
                Waveform(
                    barColor = Green400,
                    barCount = 15,
                    maxBarHeight = 120.dp,
                    barWidth = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    animated = true
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Red400)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "녹음 중…",
                        style = MaterialTheme.typography.titleMedium,
                        color = Red400,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val seconds = (state as RecallState.Recording).seconds
                    RecordingTimer(seconds = seconds)
                }
            }
        }

        // 하단 — 마이크/정지 버튼
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isRecording) "버튼을 눌러 녹음을 중지하세요"
                       else "버튼을 눌러 답변해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400
            )
            Spacer(modifier = Modifier.height(12.dp))
            SmallMicButton(
                isRecording = isRecording,
                onClick = {
                    if (isRecording) viewModel.stopRecording()
                    else viewModel.startRecording()
                }
            )
        }
    }
}

@Composable
private fun HintCard(hint: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Green50
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Green600,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    text = "힌트",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green600,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Green900
                )
            }
        }
    }
}

@Composable
private fun SmallMicButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val outerColor = if (isRecording) Red400.copy(alpha = 0.18f) else Green400.copy(alpha = 0.18f)
    val innerColor = if (isRecording) Red400 else Green400
    Box(
        modifier = Modifier
            .size(108.dp)
            .clip(CircleShape)
            .background(outerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(innerColor),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // 정지 아이콘 — 흰 사각형
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandWhite)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
