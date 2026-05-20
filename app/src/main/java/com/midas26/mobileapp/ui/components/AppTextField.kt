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
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Green400

/**
 * zip의 `Widget.App.TextInput` (OutlinedBox) 스타일에 대응.
 *
 * - 코너 16dp (기존 12dp), 포커스 시 green_400 / 비포커스 시 gray_200
 * - 본문 19sp, label 18sp 로 노년층 가독성 우선
 * - leading icon 지원, password 토글 지원, 에러 텍스트 지원
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
    errorText: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isError = errorText != null

    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (enabled && (maxLength == null || new.length <= maxLength)) onValueChange(new)
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        singleLine = singleLine,
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = Gray400,
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
                        contentDescription = null,
                        tint = Gray400,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        shape = RoundedCornerShape(16.dp),
        isError = isError,
        supportingText = supportingTextSlot(errorText = errorText, helperText = helperText),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Green400,
            unfocusedBorderColor = Gray200,
            focusedLabelColor = Green400,
            unfocusedLabelColor = Gray400,
            cursorColor = Green400,
            focusedLeadingIconColor = Green400,
            unfocusedLeadingIconColor = Gray400
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

/**
 * supportingText 슬롯을 안전하게 반환.
 * - errorText 가 우선
 * - 없으면 helperText
 * - 둘 다 null 이면 슬롯 자체를 null 로 두어 빈 공간을 차지하지 않게 함
 */
private fun supportingTextSlot(
    errorText: String?,
    helperText: String?
): (@androidx.compose.runtime.Composable () -> Unit)? {
    val message = errorText ?: helperText ?: return null
    return { Text(text = message, style = MaterialTheme.typography.bodySmall) }
}
