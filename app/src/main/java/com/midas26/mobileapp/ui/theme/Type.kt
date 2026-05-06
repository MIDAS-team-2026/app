package com.midas26.mobileapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Midas 앱 타이포그래피 — 노년층 가독성 우선.
 *
 * 모든 본문/라벨이 기존보다 약 25~30% 커지고, 줄간격(lineHeight)도
 * 1.4~1.5배 비율을 유지하여 읽기 편하게 구성되어 있습니다.
 *
 * 추후 Pretendard 폰트 리소스가 추가되면 [FontFamily.Default] 부분을
 * `FontFamily(Font(R.font.pretendard, ...))` 로 교체하면 됩니다.
 */
val Typography = Typography(
    // 스플래시 등 큰 브랜드 텍스트
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    // 화면 메인 헤딩 (예: "다시 만나서 반가워요")
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp
    ),
    // 카드/섹션 제목
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    // 역할 카드 타이틀 등
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    // 입력 필드 본문 텍스트
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp
    ),
    // 일반 본문 (부제, 설명문)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp
    ),
    // 보조/캡션
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // 버튼 라벨
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // 작은 라벨 (단계 표시 등)
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
)
