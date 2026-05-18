package com.midas26.mobileapp.ui.voicechat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Green100
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Red400

/**
 * 음성 대화 화면 상단 앱바 — ← + "음성 대화" + 우측 시간(옵션).
 */
@Composable
fun VoiceChatTopBar(
    onBack: () -> Unit,
    rightLabel: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
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
                    tint = AppColor.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "음성 대화",
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (rightLabel != null) {
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

/** AI 아바타 — 녹색 원 + "AI" 텍스트. */
@Composable
fun AiAvatar(size: Dp = 44.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = Green400
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "AI",
                color = BrandWhite,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

/**
 * 채팅 말풍선 한 줄.
 * - AI 메시지: 좌측 정렬, AI 아바타 + Green50 말풍선
 * - 사용자 메시지: 우측 정렬, Green400 말풍선 흰 텍스트
 * - faded: 흐린 회색 텍스트 (녹음/STT 중 이전 대화 비활성화 표시)
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    faded: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    if (message.from == Sender.AI) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            AiAvatar(size = 44.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = 6.dp, topEnd = 20.dp,
                    bottomStart = 20.dp, bottomEnd = 20.dp
                ),
                color = if (message.isSpeaking) Green100 else Green50,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            ) {
                if (message.isLoading) {
                    LoadingDots()
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (faded) AppColor.textTertiary else AppColor.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        if (message.isSpeaking) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SpeakingIndicator()
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 6.dp,
                    bottomStart = 20.dp, bottomEnd = 20.dp
                ),
                color = Green400,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrandWhite,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/** AI 입력 중 점 3개 (펄스 애니메이션). */
@Composable
private fun LoadingDots() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition(label = "dots")
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Green400.copy(alpha = alpha))
            )
        }
    }
}

/** TTS 재생 중 표시 — 작은 음파 막대 + "AI가 말하고 있어요". */
@Composable
private fun SpeakingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Waveform(
            barColor = Green400,
            barCount = 5,
            maxBarHeight = 16.dp,
            barWidth = 4.dp,
            modifier = Modifier.height(20.dp),
            animated = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AI가 말하고 있어요...",
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.accentDark,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 음파 시각화 — 여러 개의 막대를 다양한 높이로 보여줌.
 * idle 상태: 회색 막대 정적
 * recording 상태: 빨간 막대 애니메이션
 */
@Composable
fun Waveform(
    barColor: Color,
    barCount: Int = 15,
    maxBarHeight: Dp = 64.dp,
    barWidth: Dp = 14.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    val baseHeights = remember(barCount) {
        // 시드값 기반 가짜 음파 패턴
        List(barCount) { idx ->
            val pattern = listOf(0.25f, 0.6f, 0.4f, 0.85f, 0.5f, 1f, 0.7f, 0.45f, 0.8f, 0.35f, 0.55f, 0.9f, 0.5f, 0.7f, 0.4f)
            pattern[idx % pattern.size]
        }
    }

    val transition = if (animated) rememberInfiniteTransition(label = "wave") else null
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        baseHeights.forEachIndexed { idx, base ->
            val factor = if (transition != null) {
                val v by transition.animateFloat(
                    initialValue = base * 0.6f,
                    targetValue = base,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600 + (idx * 50), easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$idx"
                )
                v
            } else base
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxBarHeight * factor)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * 큰 마이크 / 정지 버튼 (160dp 외곽 + 132dp 내부 원).
 * - mode = Mic  : 외곽 Green400, 내부 Green600, Mic 아이콘
 * - mode = Stop : 빨간 원 + Stop 아이콘
 */
@Composable
fun BigActionButton(
    mode: BigActionMode,
    onClick: () -> Unit
) {
    val outerColor: Color
    val innerColor: Color
    val icon: ImageVector
    when (mode) {
        BigActionMode.Mic -> {
            outerColor = Green400
            innerColor = Green600
            icon = Icons.Default.Mic
        }
        BigActionMode.Stop -> {
            outerColor = Red400.copy(alpha = 0.18f)
            innerColor = Red400
            icon = Icons.Default.Stop
        }
    }
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(outerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(innerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandWhite,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

enum class BigActionMode { Mic, Stop }

/**
 * 녹음 중 펄스 — 빨간 동심원 3개 (확대-축소 애니메이션).
 */
@Composable
fun RecordingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val outerScale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "outer"
    )
    val midScale by transition.animateFloat(
        initialValue = 0.95f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "mid"
    )
    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((220 * outerScale).dp.coerceAtLeast(160.dp))
                .clip(CircleShape)
                .background(Red400.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size((180 * midScale).dp.coerceAtLeast(140.dp))
                .clip(CircleShape)
                .background(Red400.copy(alpha = 0.18f))
        )
    }
}

/** 녹음 타이머 표시 — 00:07 형식. */
@Composable
fun RecordingTimer(seconds: Int) {
    val mm = (seconds / 60).toString().padStart(2, '0')
    val ss = (seconds % 60).toString().padStart(2, '0')
    Text(
        text = "$mm:$ss",
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = AppColor.textPrimary
    )
}

/** STT 결과 인용 카드 — ❝ + 본문. */
@Composable
fun SttQuoteCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = Green50
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "❝", fontSize = 26.sp, color = Green400, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** STT 보조 액션 (다시 듣기 / 수정하기). */
@Composable
fun SttSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = AppColor.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** "다시 녹음하기" 보조 와이드 버튼. */
@Composable
fun WideSecondaryButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Gray100
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = AppColor.textTertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

