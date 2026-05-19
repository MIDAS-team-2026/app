package com.midas26.mobileapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppLightColorScheme = lightColorScheme(
    primary = Green400,
    onPrimary = BrandWhite,
    primaryContainer = Green50,
    onPrimaryContainer = Green900,
    secondary = Green200,
    onSecondary = Green900,
    secondaryContainer = Green100,
    onSecondaryContainer = Green900,
    tertiary = Green400,
    background = BrandWhite,
    onBackground = Gray800,
    surface = BrandWhite,
    onSurface = Gray800,
    surfaceVariant = Gray50,
    onSurfaceVariant = Gray600,
    outline = Gray200,
    error = Red400,
    onError = BrandWhite,
    errorContainer = Red50,
    onErrorContainer = Red400
)

private val AppHighContrastColorScheme = lightColorScheme(
    primary = Green600,
    onPrimary = BrandWhite,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Green500,
    onSecondary = BrandWhite,
    secondaryContainer = Green100,
    onSecondaryContainer = Green900,
    tertiary = Green600,
    background = BrandWhite,
    onBackground = BrandBlack,
    surface = BrandWhite,
    onSurface = BrandBlack,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray800,
    outline = Gray600,
    error = Red400,
    onError = BrandWhite,
    errorContainer = Red50,
    onErrorContainer = Red400
)

private val AppDarkColorScheme = darkColorScheme(
    primary = Green200,
    onPrimary = Green900,
    primaryContainer = Green600,
    onPrimaryContainer = Green50,
    secondary = Green100,
    onSecondary = Green900,
    background = Gray900,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,
    error = Red400,
    onError = BrandWhite
)

/**
 * 앱 전역 테마.
 * 기존 프로젝트와의 호환성을 위해 [AppTheme] 이름을 유지합니다.
 *
 * 주의: 디자인 가이드(밝은 배경 + 라이트 상태바)에 맞추기 위해 dynamicColor 기본값은 false.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val highContrast = LocalHighContrast.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> AppDarkColorScheme
        highContrast -> AppHighContrastColorScheme
        else -> AppLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography.scaled(LocalFontSizeScale.current),
        content = content
    )
}

