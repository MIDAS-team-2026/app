package com.midas26.mobileapp.ui.voicechat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Red400
import com.midas26.mobileapp.ui.theme.AppColor

/**
 * 음성 대화 통합 화면.
 * 상단(앱바) + 채팅 리스트는 모든 상태에서 공유, 하단 영역만 상태별로 다르게 표시.
 *
 *  - Idle      : "버튼을 눌러 말씀해 주세요" + 정적 음파 + 큰 마이크 버튼
 *  - Recording : 빨간 펄스 + "녹음 중..." + 타이머 + 빨간 음파 + 정지 버튼
 *  - Reviewing : "이렇게 말씀하셨나요?" + 인용 카드 + 다시 듣기/수정 + 전송/다시 녹음
 *  - Playing   : 마지막 AI 메시지에 음파 표시 + "다시 듣기 / 답변하기" 버튼
 */
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    onDisconnected: () -> Unit = {},
    viewModel: VoiceChatViewModel = viewModel()
) {
    val state = viewModel.state
    val messages = viewModel.messages
    val faded = state is VoiceChatState.Recording || state is VoiceChatState.Reviewing

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        VoiceChatTopBar(
            onBack = onBack,
            rightLabel = if (state is VoiceChatState.Idle) "오후 2:30" else null
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColor.divider)
        )

        // 채팅 영역 — Reviewing 상태에선 마지막 AI 한 줄만 보여 인용 카드에 집중
        val visibleMessages = if (state is VoiceChatState.Reviewing) {
            messages.lastOrNull { it.from == Sender.AI }?.let { listOf(it) }.orEmpty()
        } else messages

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.Top
        ) {
            items(visibleMessages) { msg ->
                ChatBubble(message = msg, faded = faded)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColor.divider)
        )

        // 하단 영역 — 상태별 분기
        when (state) {
            is VoiceChatState.Idle      -> IdleBottom(onMicClick = viewModel::startRecording)
            is VoiceChatState.Recording -> RecordingBottom(
                seconds = viewModel.recordingSeconds,
                onStopClick = viewModel::stopRecording
            )
            is VoiceChatState.Reviewing -> ReviewingBottom(
                sttText = state.sttText,
                onSend = viewModel::sendStt,
                onRetry = viewModel::retryRecording,
                onReplaySource = { /* 더미: 음성 다시 듣기 — 백엔드 연동 시 구현 */ },
                onEdit = { /* 더미: 텍스트 수정 화면 — 추후 구현 */ }
            )
            is VoiceChatState.Playing   -> PlayingBottom(
                onReplay = viewModel::replayLastAi,
                onAnswer = viewModel::finishPlaying
            )
        }
    }
}

// ---- 상태별 하단 ----

@Composable
private fun IdleBottom(onMicClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "버튼을 눌러 말씀해 주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Waveform(
            barColor = Gray200,
            barCount = 13,
            maxBarHeight = 40.dp,
            barWidth = 12.dp,
            modifier = Modifier.height(56.dp),
            animated = false
        )
        Spacer(modifier = Modifier.height(16.dp))
        BigActionButton(mode = BigActionMode.Mic, onClick = onMicClick)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RecordingBottom(seconds: Int, onStopClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "● 녹음 중...",
            style = MaterialTheme.typography.titleMedium,
            color = Red400,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        RecordingTimer(seconds = seconds)
        Spacer(modifier = Modifier.height(12.dp))
        Waveform(
            barColor = Red400,
            barCount = 15,
            maxBarHeight = 56.dp,
            barWidth = 12.dp,
            modifier = Modifier.height(72.dp),
            animated = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            RecordingPulse()
            BigActionButton(mode = BigActionMode.Stop, onClick = onStopClick)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "버튼을 눌러 녹음을 중지하세요",
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewingBottom(
    sttText: String,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onReplaySource: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "이렇게 말씀하셨나요?",
            style = MaterialTheme.typography.titleLarge,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "다르면 아래 버튼을 눌러주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))

        SttQuoteCard(text = sttText)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SttSecondaryButton(
                label = "다시 듣기",
                leadingIcon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onReplaySource,
                modifier = Modifier.weight(1f)
            )
            SttSecondaryButton(
                label = "수정하기",
                leadingIcon = Icons.Default.Edit,
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            AppPrimaryButton(
                text = "전송하기",
                onClick = onSend
            )
            Spacer(modifier = Modifier.height(10.dp))
            WideSecondaryButton(label = "다시 녹음하기", onClick = onRetry)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PlayingBottom(
    onReplay: () -> Unit,
    onAnswer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SttSecondaryButton(
            label = "다시 듣기",
            leadingIcon = Icons.AutoMirrored.Filled.VolumeUp,
            onClick = onReplay,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.weight(1f)) {
            AppPrimaryButton(
                text = "답변하기",
                onClick = onAnswer
            )
        }
    }
}
