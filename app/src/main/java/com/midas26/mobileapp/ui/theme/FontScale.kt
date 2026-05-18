package com.midas26.mobileapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle

enum class FontSizeLevel(val scale: Float) {
    SMALL(0.88f),
    NORMAL(1.0f),
    LARGE(1.15f),
    XLARGE(1.30f);

    companion object {
        fun fromIndex(index: Int): FontSizeLevel = entries.getOrElse(index) { NORMAL }
    }
}

val LocalFontSizeScale = compositionLocalOf { FontSizeLevel.NORMAL }

private fun TextStyle.scale(factor: Float): TextStyle = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor
)

fun Typography.scaled(level: FontSizeLevel): Typography {
    val s = level.scale
    return copy(
        displaySmall  = displaySmall.scale(s),
        headlineSmall = headlineSmall.scale(s),
        titleLarge    = titleLarge.scale(s),
        titleMedium   = titleMedium.scale(s),
        bodyLarge     = bodyLarge.scale(s),
        bodyMedium    = bodyMedium.scale(s),
        bodySmall     = bodySmall.scale(s),
        labelLarge    = labelLarge.scale(s),
        labelMedium   = labelMedium.scale(s)
    )
}
