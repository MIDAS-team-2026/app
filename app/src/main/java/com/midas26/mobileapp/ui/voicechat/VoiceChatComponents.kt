package com.midas26.mobileapp.ui.voicechat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Green100
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Red400
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 음성 대화 화면 상단 앱바 — ← + "음성 대화" + 우측 시간(옵션).
 */
@Composable
fun VoiceChatTopBar(
    onBack: () -> Unit,
    rightLabel: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Green600, Green400)
                )
            )
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
                    tint = BrandWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "음성 대화",
                style = MaterialTheme.typography.titleLarge,
                color = BrandWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (rightLabel != null) {
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandWhite.copy(alpha = 0.85f),
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
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val outerColor: Color
    val innerColor: Color
    val icon: ImageVector
    when (mode) {
        BigActionMode.Mic -> {
            outerColor = if (enabled) Green400 else Gray200
            innerColor = if (enabled) Green600 else Gray400
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
            .clickable(enabled = enabled, onClick = onClick),
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


// ════════════════════════════════════════════════════════════════════════════
//  캐릭터 중심 UI 컴포넌트
// ════════════════════════════════════════════════════════════════════════════

/**
 * AI 말풍선 박스 — Figma 디자인 기준.
 *
 * 녹색 테두리(2dp), 둥근 모서리(10dp) 박스 안에 AI 질문/답변을 큰 글씨로 표시.
 * [highlighted] = true (AI 말하는 중) 이면 테두리가 굵어지고 배경이 펄스 애니메이션.
 * [onClick] 이 있으면 탭 시 AI 재생 콜백을 호출한다.
 */
@Composable
fun AiSpeechBubble(
    text: String,
    isLoading: Boolean = false,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 하이라이트 펄스 — 항상 transition 생성, highlighted 여부에 따라 적용
    val hlTransition = rememberInfiniteTransition(label = "bubble_hl")
    val pulseAlpha by hlTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble_hl_alpha"
    )

    val borderWidth = if (highlighted) 3.dp else 2.dp
    val borderColor = if (highlighted) Green400 else Green400.copy(alpha = 0.55f)
    val bgFill     = if (highlighted) Green400.copy(alpha = pulseAlpha) else Color.Transparent

    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgFill)
            .border(borderWidth, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ) else Modifier)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            // 로딩 중 — 점 3개 애니메이션
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                val transition = rememberInfiniteTransition(label = "bubble_dots")
                repeat(3) { i ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                delayMillis = i * 180,
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bdot$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Green400.copy(alpha = alpha))
                    )
                }
            }
        } else {
            val fontScale = LocalFontSizeScale.current.scale
            Text(
                text = text,
                fontSize = (30f * fontScale).sp,
                lineHeight = (42f * fontScale).sp,
                textAlign = TextAlign.Center,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Normal
            )
        }
    }

    // 꼬리 삼각형 — 말풍선 하단 중앙에서 캐릭터 방향으로
    // offset(y = -borderWidth) 로 버블 border 위에 겹쳐서 이음새 선 제거
    Canvas(
        modifier = Modifier
            .size(width = 26.dp, height = 14.dp)
            .offset(y = -borderWidth)
    ) {
        val tailBorderColor = if (highlighted) Green400 else Green400.copy(alpha = 0.55f)
        val tailFillColor   = if (highlighted) Green400.copy(alpha = pulseAlpha) else BrandWhite

        // 테두리 삼각형
        val borderPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(borderPath, color = tailBorderColor)

        // 내부 채움 삼각형 — 상단을 버블 안쪽 배경색으로 채워 이음새 완전 제거
        val inset = 2.5.dp.toPx()
        val fillPath = Path().apply {
            moveTo(inset, 0f)
            lineTo(size.width - inset, 0f)
            lineTo(size.width / 2f, size.height - inset * 1.2f)
            close()
        }
        drawPath(fillPath, color = tailFillColor)
    }
    } // Column 닫기
}

/**
 * 캐릭터 이미지 — 별 몸통 위에 눈·입을 레이어로 합성 + 상태별 애니메이션.
 *
 * Idle / Processing  : float (위아래) + blink (눈 깜빡)
 * Playing            : float + blink + talk (입 움직임)
 * Recording          : pulse (확대/축소) + blink, float 없음
 *
 * [onClick] 이 있으면 탭 시 콜백을 호출한다 (AI 다시 말하기 등).
 */
@Composable
fun CharacterImage(
    state: VoiceChatState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isFloating  = state is VoiceChatState.Idle || state is VoiceChatState.Processing || state is VoiceChatState.Playing
    val isTalking   = state is VoiceChatState.Playing
    val isListening = state is VoiceChatState.Recording

    val transition = rememberInfiniteTransition(label = "char")

    // ── float: 0 → -10dp → 0 (3.6 s, FastOutSlowIn, Reverse) ──────────
    val floatOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // ── blink: 4.2 s 주기, 94-98% 구간에서 감은 눈 ──────────────────────
    // 0f = eye_open 표시, 1f = eye_closed 표시
    val blinkProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0
                0f at 3700                          // 88 %
                1f at 3950 using LinearEasing       // 94 %  (감음)
                0f at 4200                          // 100 % (뜸)
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // ── talk: 0.42 s 주기, 50% 경계에서 입 교체 ─────────────────────────
    // 0f = mouth_smile, 1f = mouth_open
    val talkProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 420
                0f at 0
                0f at 209
                1f at 210 using LinearEasing
                1f at 420
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "talk"
    )

    // ── pulse: 1 → 1.06 → 1 (1.1 s, FastOutSlowIn, Reverse) ────────────
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 눈: blink 진행 중(>0.5)이면 감은 눈, 아니면 뜬 눈
    //     Recording에서는 기본 뜬 눈 + blink 적용
    val eyeClosed  = blinkProgress > 0.5f
    val eyeRes     = if (eyeClosed) R.drawable.char_eye_closed else R.drawable.char_eye_open

    // 입: Playing 상태에서 talk 진행 중(>0.5)이면 벌린 입
    val mouthOpen  = isTalking && talkProgress > 0.5f
    val mouthRes   = if (mouthOpen) R.drawable.char_mouth_open else R.drawable.char_mouth_smile

    val density = LocalDensity.current
    val floatOffsetPx = with(density) { floatOffset.dp.toPx() }
    val scale = if (isListening) pulseScale else 1f

    val res = LocalContext.current.resources
    val bodyBitmap   = remember { ImageBitmap.imageResource(res, R.drawable.char_body) }
    val eyeOpenBmp   = remember { ImageBitmap.imageResource(res, R.drawable.char_eye_open) }
    val eyeClosedBmp = remember { ImageBitmap.imageResource(res, R.drawable.char_eye_closed) }
    val mouthSmileBmp = remember { ImageBitmap.imageResource(res, R.drawable.char_mouth_smile) }
    val mouthOpenBmp  = remember { ImageBitmap.imageResource(res, R.drawable.char_mouth_open) }

    val eyeBitmap   = if (eyeClosed) eyeClosedBmp else eyeOpenBmp
    val mouthBitmap = if (mouthOpen) mouthOpenBmp else mouthSmileBmp

    Box(
        modifier = modifier
            .size(260.dp)
            .graphicsLayer(
                translationY = if (isFloating) floatOffsetPx else 0f,
                scaleX = scale,
                scaleY = scale
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // ① 별 몸통
        Image(
            bitmap = bodyBitmap,
            contentDescription = "또바기 캐릭터",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
        // ② 눈 레이어 (중앙 기준으로 약간 크게)
        Image(
            bitmap = eyeBitmap,
            contentDescription = null,
            modifier = Modifier.requiredSize(340.dp),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
        // ③ 입 레이어 (중앙 기준으로 약간 크게)
        Image(
            bitmap = mouthBitmap,
            contentDescription = null,
            modifier = Modifier.requiredSize(340.dp),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
    }
}

/**
 * 원형 마이크 버튼 — Figma 하단 마이크 영역.
 *
 * 연두색(#F0FAE8) 원형 배경에 마이크 아이콘을 표시한다.
 * [enabled] = false 면 회색으로 비활성화된다.
 */
@Composable
fun MicCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val bgColor = if (enabled) Green50 else Gray200
    val iconTint = if (enabled) Green600 else Gray400

    Box(
        modifier = Modifier
            .size(130.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "마이크",
            tint = iconTint,
            modifier = Modifier.size(64.dp)
        )
    }
}

