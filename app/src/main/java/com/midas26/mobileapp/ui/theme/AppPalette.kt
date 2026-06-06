package com.midas26.mobileapp.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 앱 전체 색상 팔레트 추상화.
 *
 * 모드별 인스턴스:
 *  - [NormalPalette]   : 기본 라이트 모드
 *  - [ContrastPalette] : 고대비 모드 (채도 강화 + WCAG 대비 충족)
 *  - [DarkPalette]     : 다크 모드 (추후 구현)
 *
 * Theme.kt에서 [LocalAppPalette]로 제공되며,
 * 어느 Composable에서나 `LocalAppPalette.current.green400` 형태로 접근한다.
 */
data class AppPalette(
    // ── Green ──────────────────────────────────────
    val green50:  Color,
    val green100: Color,
    val green200: Color,
    val green400: Color,
    val green500: Color,
    val green600: Color,
    val green700: Color,
    val green900: Color,

    // ── Gray ───────────────────────────────────────
    val gray50:  Color,
    val gray100: Color,
    val gray200: Color,
    val gray400: Color,
    val gray600: Color,
    val gray800: Color,
    val gray900: Color,

    // ── Amber ──────────────────────────────────────
    val amber50:  Color,
    val amber100: Color,
    val amber200: Color,
    val amber400: Color,
    val amber500: Color,
    val amber600: Color,
    val amber900: Color,

    // ── Red ────────────────────────────────────────
    val red50:  Color,
    val red400: Color,

    // ── Base ───────────────────────────────────────
    val brandWhite: Color,
    val brandBlack: Color,
)

// ── 기본 라이트 팔레트 ────────────────────────────────────────────────────────

val NormalPalette = AppPalette(
    green50  = Green50,
    green100 = Green100,
    green200 = Green200,
    green400 = Green400,
    green500 = Green500,
    green600 = Green600,
    green700 = Green700,
    green900 = Green900,

    gray50  = Gray50,
    gray100 = Gray100,
    gray200 = Gray200,
    gray400 = Gray400,
    gray600 = Gray600,
    gray800 = Gray800,
    gray900 = Gray900,

    amber50  = Amber50,
    amber100 = Amber100,
    amber200 = Amber200,
    amber400 = Amber400,
    amber500 = Amber500,
    amber600 = Amber600,
    amber900 = Amber900,

    red50  = Red50,
    red400 = Red400,

    brandWhite = BrandWhite,
    brandBlack = BrandBlack,
)

// ── 고대비 팔레트 ─────────────────────────────────────────────────────────────
// Green: ContrastGreen 사용 (채도 강화 + WCAG 대비 충족)
// Gray:  한 단계씩 더 진하게
// Amber/Red: 채도·명도 강화

val ContrastPalette = AppPalette(
    green50  = ContrastGreen50,
    green100 = ContrastGreen100,
    green200 = ContrastGreen200,
    green400 = ContrastGreen400,
    green500 = ContrastGreen500,
    green600 = ContrastGreen600,
    green700 = ContrastGreen700,
    green900 = ContrastGreen900,

    gray50  = Gray100,
    gray100 = Gray200,
    gray200 = Gray400,
    gray400 = Gray600,
    gray600 = Gray800,
    gray800 = Gray900,
    gray900 = BrandBlack,

    amber50  = Color(0xFFFDE9A8),
    amber100 = Color(0xFFFBD57A),
    amber200 = Color(0xFFF5BF34),
    amber400 = Color(0xFFCC8C00),
    amber500 = Color(0xFFAA7000),
    amber600 = Color(0xFF7A5100),
    amber900 = Color(0xFF4A3000),

    red50  = Color(0xFFFFD6D6),
    red400 = Color(0xFFCC0000),

    brandWhite = BrandWhite,
    brandBlack = BrandBlack,
)

// ── 다크 팔레트 ───────────────────────────────────────────────────────────────
// 추후 구현 — 현재는 NormalPalette와 동일하게 설정
// 다크 모드 작업 시 이 값들만 교체하면 전체 반영됨

val DarkPalette = AppPalette(
    green50  = Green900,
    green100 = Green700,
    green200 = Green600,
    green400 = Green200,
    green500 = Green100,
    green600 = Green50,
    green700 = Green50,
    green900 = Green50,

    gray50  = Gray900,
    gray100 = Gray800,
    gray200 = Gray600,
    gray400 = Gray400,
    gray600 = Gray200,
    gray800 = Gray100,
    gray900 = Gray50,

    amber50  = Amber900,
    amber100 = Amber600,
    amber200 = Amber500,
    amber400 = Amber400,
    amber500 = Amber200,
    amber600 = Amber100,
    amber900 = Amber50,

    red50  = Color(0xFF4A0000),
    red400 = Color(0xFFFF6B6B),

    brandWhite = BrandBlack,
    brandBlack = BrandWhite,
)

/** 현재 활성 팔레트. Theme에서 모드에 따라 자동 교체된다. */
val LocalAppPalette = compositionLocalOf { NormalPalette }
