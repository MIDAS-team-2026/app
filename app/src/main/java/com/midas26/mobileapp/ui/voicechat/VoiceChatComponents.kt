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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import android.graphics.BlurMaskFilter
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
                    colors = listOf(AppColor.accentDark, AppColor.greenPrimary)
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
        color = AppColor.greenPrimary
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
 * - AI 메시지: 좌측 정렬, AI 아바타 + AppColor.greenSurface 말풍선
 * - 사용자 메시지: 우측 정렬, AppColor.greenPrimary 말풍선 흰 텍스트
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
                color = if (message.isSpeaking) AppColor.greenSurfaceVariant else AppColor.greenSurface,
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
                color = AppColor.greenPrimary,
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
                    .background(AppColor.greenPrimary.copy(alpha = alpha))
            )
        }
    }
}

/** TTS 재생 중 표시 — 작은 음파 막대 + "AI가 말하고 있어요". */
@Composable
private fun SpeakingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Waveform(
            barColor = AppColor.greenPrimary,
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

private fun Modifier.circleGlow(
    color: Color,
    blur: Dp = 24.dp,
    spread: Dp = 0.dp,
    offsetY: Dp = 12.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().also {
            it.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
                setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), color.toArgb())
            }
        }
        canvas.drawCircle(
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + offsetY.toPx() / 2f),
            radius = size.minDimension / 2f + spread.toPx(),
            paint  = paint
        )
    }
}

/**
 * 텍스트 입력 행 — 텍스트필드 + 전송 버튼.
 * 기존 마이크 녹음 버튼을 대체한다 (stt_debug: 녹음/STT 제거, 텍스트 채팅으로 대체).
 */
@Composable
fun TextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val canSend = enabled && value.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColor.greenSurface,
            modifier = Modifier.weight(1f)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColor.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                cursorBrush = SolidColor(AppColor.accentDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "메시지를 입력하세요",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColor.textTertiary
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (canSend) AppColor.accentDark else AppColor.textTertiary)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = BrandWhite,
                modifier = Modifier.size(24.dp)
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
    val greenPrimary = AppColor.greenPrimary
    val borderColor = if (highlighted) greenPrimary else greenPrimary.copy(alpha = 0.55f)
    val bgFill     = if (highlighted) greenPrimary.copy(alpha = pulseAlpha) else Color.Transparent

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
                            .background(AppColor.greenPrimary.copy(alpha = alpha))
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
        val tailBorderColor = if (highlighted) greenPrimary else greenPrimary.copy(alpha = 0.55f)
        val tailFillColor   = if (highlighted) greenPrimary.copy(alpha = pulseAlpha) else BrandWhite

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

    // 눈: blink 진행 중(>0.5)이면 감은 눈, 아니면 뜬 눈
    val eyeClosed  = blinkProgress > 0.5f
    val eyeRes     = if (eyeClosed) R.drawable.char_eye_closed else R.drawable.char_eye_open

    // 입: Playing 상태에서 talk 진행 중(>0.5)이면 벌린 입
    val mouthOpen  = isTalking && talkProgress > 0.5f
    val mouthRes   = if (mouthOpen) R.drawable.char_mouth_open else R.drawable.char_mouth_smile

    val density = LocalDensity.current
    val floatOffsetPx = with(density) { floatOffset.dp.toPx() }

    // 누름 감지 — onClick 있을 때만 (tapToReplay 켜진 경우)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pressScale"
    )

    val scale = pressScale

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
            .circleGlow(color = Color(0xFFFFCC00).copy(alpha = 0.25f), blur = 40.dp, spread = -20.dp, offsetY = 12.dp)
            .graphicsLayer(
                translationY = if (isFloating) floatOffsetPx else 0f,
                scaleX = scale,
                scaleY = scale
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    indication = null,
                    interactionSource = interactionSource,
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

