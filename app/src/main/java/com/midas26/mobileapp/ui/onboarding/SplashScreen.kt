package com.midas26.mobileapp.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay

private val JuaFamily   = FontFamily(Font(R.font.jua_regular,  FontWeight.Normal))
private val GaeguFamily = FontFamily(Font(R.font.gaegu_bold,   FontWeight.Bold))

private val Cream1    = Color(0xFFFBF4E6)
private val Cream2    = Color(0xFFF5EAD3)
private val Cream3    = Color(0xFFEDE0C2)
private val BrandInk  = Color(0xFF5B3621)
private val BrandInk2 = Color(0xFF7A4F30)
private val Sprout1   = Color(0xFF94C560)
private val Sprout2   = Color(0xFF5FA83A)
private val Sprout3   = Color(0xFF7CC04A)

/**
 * 스플래시 화면 — "똑똑" 브랜드 디자인 (A1 크림 메인)
 */
@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(1800)
        val prefs = PrefsManager.from(context)
        when {
            prefs.isLoggedIn()        -> onNavigateToHome()
            prefs.hasSeenOnboarding() -> onNavigateToLogin()
            else                      -> onNavigateToOnboarding()
        }
    }

    // 점 3개 순차 페이드 애니메이션 (1.2s 루프)
    val infinite = rememberInfiniteTransition(label = "dots")
    val dot1 by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.9f at 0   using LinearEasing
            0.25f at 300 using LinearEasing
            0.9f at 600  using LinearEasing
            0.9f at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d1"
    )
    val dot2 by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.5f at 0   using LinearEasing
            0.9f at 300  using LinearEasing
            0.25f at 600 using LinearEasing
            0.5f at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d2"
    )
    val dot3 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.25f at 0  using LinearEasing
            0.5f at 300  using LinearEasing
            0.9f at 600  using LinearEasing
            0.25f at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Cream1, Cream2, Cream3)))
    ) {
        // 데코 블롭
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 80.dp, y = 60.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0xFF7CC04A).copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = (-60).dp, y = (-80).dp)
                .align(Alignment.BottomStart)
                .clip(CircleShape)
                .background(Color(0xFFB47650).copy(alpha = 0.08f))
        )

        // 메인 콘텐츠
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 캐릭터 이미지 + 그림자
            Box(
                modifier = Modifier.width(240.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // 바닥 그림자
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(168.dp)
                        .height(14.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    BrandInk.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                Image(
                    painter = painterResource(R.drawable.ttobak_healthy),
                    contentDescription = "또바기 캐릭터",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 워드마크 행
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DecoStrokes(rotation = -12f)

                // 똑똑 텍스트 + 위에 새싹
                Box(contentAlignment = Alignment.TopCenter) {
                    // 새싹 (텍스트 위에 살짝 올라가게)
                    Canvas(
                        modifier = Modifier
                            .size(width = 32.dp, height = 38.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-30).dp)
                            .rotate(-6f)
                    ) { drawSproutIcon() }

                    Text(
                        text = "똑똑",
                        fontFamily = JuaFamily,
                        fontSize = 72.sp,
                        color = BrandInk,
                        lineHeight = 72.sp
                    )
                }

                DecoStrokes(rotation = 12f, flip = true)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 태그라인
            Text(
                text = "매일 똑똑하게, 건강한 기억",
                fontFamily = GaeguFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = BrandInk2
            )
        }

        // 하단 로딩 점 3개
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(dot1, dot2, dot3).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandInk.copy(alpha = alpha))
                )
            }
        }
    }
}

private fun DrawScope.drawSproutIcon() {
    val cx = size.width / 2f
    val h  = size.height

    // 줄기
    drawLine(
        color = Sprout2,
        start = Offset(cx, h * 0.9f),
        end   = Offset(cx, h * 0.5f),
        strokeWidth = 3.dp.toPx(),
        cap   = StrokeCap.Round
    )
    // 왼쪽 잎
    drawOval(
        color   = Sprout3,
        topLeft = Offset(size.width * 0.02f, h * 0.1f),
        size    = Size(size.width * 0.42f, h * 0.52f)
    )
    // 오른쪽 잎
    drawOval(
        color   = Sprout1,
        topLeft = Offset(size.width * 0.38f, h * 0.0f),
        size    = Size(size.width * 0.55f, h * 0.55f)
    )
}

@Composable
private fun DecoStrokes(rotation: Float, flip: Boolean = false) {
    Row(
        modifier = Modifier.rotate(rotation),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val strokes = if (flip) listOf(18, 14) else listOf(14, 18)
        strokes.forEach { w ->
            Box(
                modifier = Modifier
                    .width(w.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Sprout2)
            )
        }
    }
}
