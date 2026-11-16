package com.midas26.mobileapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.ui.theme.AppColor

/**
 * 앱 공통 입력 필드.
 * 비밀번호 표시 전환, 입력 길이 제한, 오류/도움말 문구를 지원한다.
 * visualTransformation은 일반 입력 필드에만 적용하며 기본값은 변환 없음이다.
 */
@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    helperText: String? = null,
    errorText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isError = errorText != null

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (enabled && (maxLength == null || newValue.length <= maxLength)) {
                onValueChange(newValue)
            }
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        label = {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        },
        singleLine = singleLine,
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                        tint = AppColor.textTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else null,
        visualTransformation = when {
            isPassword && !passwordVisible -> PasswordVisualTransformation()
            isPassword -> VisualTransformation.None
            else -> visualTransformation
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        shape = RoundedCornerShape(16.dp),
        isError = isError,
        supportingText = supportingTextSlot(errorText, helperText),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColor.greenPrimary,
            unfocusedBorderColor = AppColor.divider,
            focusedLabelColor = AppColor.greenPrimary,
            unfocusedLabelColor = AppColor.textTertiary,
            cursorColor = AppColor.greenPrimary,
            focusedLeadingIconColor = AppColor.greenPrimary,
            unfocusedLeadingIconColor = AppColor.textTertiary
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

/** 오류 문구를 우선 표시하고, 문구가 없으면 하단 영역을 생성하지 않는다. */
private fun supportingTextSlot(
    errorText: String?,
    helperText: String?
): (@Composable () -> Unit)? {
    val message = errorText ?: helperText ?: return null
    return {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (errorText != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
