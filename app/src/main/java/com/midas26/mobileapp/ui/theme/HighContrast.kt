package com.midas26.mobileapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalHighContrast = compositionLocalOf { false }
val LocalHapticEnabled = compositionLocalOf { true }

/**
 * 고대비 모드를 인식하는 앱 전용 색상 접근자.
 * 모든 화면에서 hardcoded 색상 대신 이 객체를 사용하면
 * 고대비 토글 시 즉시 전체 화면에 반영됩니다.
 */
object AppColor {
    val textPrimary: Color
        @Composable get() = if (LocalHighContrast.current) BrandBlack else Gray800
    val textSecondary: Color
        @Composable get() = if (LocalHighContrast.current) Gray800 else Gray600
    val textTertiary: Color
        @Composable get() = if (LocalHighContrast.current) Gray600 else Gray400
    val divider: Color
        @Composable get() = if (LocalHighContrast.current) Gray600 else Gray200
    val accent: Color
        @Composable get() = if (LocalHighContrast.current) Green600 else Green400
    val accentDark: Color
        @Composable get() = if (LocalHighContrast.current) Green900 else Green600
}
