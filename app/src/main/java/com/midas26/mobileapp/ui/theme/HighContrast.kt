package com.midas26.mobileapp.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalHighContrast = compositionLocalOf { false }
val LocalHapticEnabled = compositionLocalOf { true }
val LocalTapToReplay = compositionLocalOf { true }

// null = TTS 비활성화 상태
val LocalTtsManager = compositionLocalOf<com.midas26.mobileapp.util.TtsManager?> { null }

/**
 * 앱 전역 색상 접근자.
 *
 * 모든 화면에서 하드코딩 색상 대신 이 객체를 사용하면
 * 고대비/다크 모드 전환 시 자동으로 전체 화면에 반영됩니다.
 *
 * 색상은 Theme.kt에서 모드별로 교체된 [LocalAppPalette]에서 읽어옵니다.
 * 팔레트에 없는 "비주얼 동작"(테두리, 그림자)만 [LocalHighContrast]를 직접 참조합니다.
 */
object AppColor {
    // ── 텍스트 ───────────────────────────────────────────────────────────────
    /** 제목·본문 주 텍스트 */
    val textPrimary: Color
        @Composable get() = LocalAppPalette.current.gray800

    /** 보조 설명 텍스트 */
    val textSecondary: Color
        @Composable get() = LocalAppPalette.current.gray600

    /** 힌트·비활성 텍스트 */
    val textTertiary: Color
        @Composable get() = LocalAppPalette.current.gray400

    // ── 배경·서피스 ───────────────────────────────────────────────────────────
    /** 카드·입력창 배경 등 미묘하게 들뜬 서피스 */
    val surfaceElevated: Color
        @Composable get() = LocalAppPalette.current.gray100

    // ── 선·구분자 ─────────────────────────────────────────────────────────────
    val divider: Color
        @Composable get() = LocalAppPalette.current.gray200

    // ── Green — 버튼·아이콘·말풍선 ─────────────────────────────────────────────
    /** 버튼·말풍선·테두리 등 주요 강조색 */
    val accent: Color
        @Composable get() = LocalAppPalette.current.green400

    val accentDark: Color
        @Composable get() = LocalAppPalette.current.green600

    /** alias: accent와 동일 */
    val greenPrimary: Color
        @Composable get() = LocalAppPalette.current.green400

    /** 카드·말풍선 배경 등 연한 Green 면 */
    val greenSurface: Color
        @Composable get() = LocalAppPalette.current.green50

    /** 아이콘·인디케이터 등 중간 Green */
    val greenSecondary: Color
        @Composable get() = LocalAppPalette.current.green500

    /** AI 말풍선 speaking 상태 배경 */
    val greenSurfaceVariant: Color
        @Composable get() = LocalAppPalette.current.green100

    // ── Amber ─────────────────────────────────────────────────────────────────
    val amberSurface: Color
        @Composable get() = LocalAppPalette.current.amber50

    val amberPrimary: Color
        @Composable get() = LocalAppPalette.current.amber400

    val amberDark: Color
        @Composable get() = LocalAppPalette.current.amber600

    // ── Red ───────────────────────────────────────────────────────────────────
    val errorSurface: Color
        @Composable get() = LocalAppPalette.current.red50

    val errorPrimary: Color
        @Composable get() = LocalAppPalette.current.red400

    // ── Guardian (보호자 화면 전용) ───────────────────────────────────────────
    /** 보호자 주요 액션색 (버튼·헤더 등) */
    val guardianPrimary: Color
        @Composable get() = LocalAppPalette.current.guardianAccent

    /** 보호자 진한 액션색 (그라데이션 시작점 등) */
    val guardianDark: Color
        @Composable get() = LocalAppPalette.current.guardianAccentDark

    /** 보호자 연한 서피스 (카드 배경 등) */
    val guardianSurface: Color
        @Composable get() = LocalAppPalette.current.guardianAccentLight

    // ── 카드 비주얼 동작 (팔레트 외 — 모드별 동작 차이) ─────────────────────────
    /** 고대비: 그림자 제거, 일반: 3dp 그림자 */
    val cardShadowElevation: Dp
        @Composable get() = if (LocalHighContrast.current) 0.dp else 3.dp

    /** 고대비: 검은 테두리, 일반: 없음 */
    val cardBorder: BorderStroke?
        @Composable get() = if (LocalHighContrast.current)
            BorderStroke(1.5.dp, BrandBlack)
        else null
}
