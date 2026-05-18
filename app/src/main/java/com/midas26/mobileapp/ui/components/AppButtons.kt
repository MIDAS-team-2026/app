package com.midas26.mobileapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled

/**
 * zip의 `Widget.App.Button.Primary` 스타일에 대응하는 Compose 버튼.
 * - 높이 64dp, 코너 16dp, 배경 green_400, 텍스트 onPrimary, 18sp semi-bold
 *   (노년층 가독성 우선으로 기존 52dp/14dp/15sp 대비 확대됨)
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current
    Button(
        onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green400,
            contentColor = BrandWhite,
            disabledContainerColor = Green400.copy(alpha = 0.4f),
            disabledContentColor = BrandWhite.copy(alpha = 0.7f)
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * zip의 `Widget.App.Button.Outline` 스타일에 대응.
 */
@Composable
fun AppOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current
    OutlinedButton(
        onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Green600
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * zip의 `Widget.App.Button.Text` 스타일에 대응.
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current
    TextButton(
        onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        if (content != null) content() else Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
