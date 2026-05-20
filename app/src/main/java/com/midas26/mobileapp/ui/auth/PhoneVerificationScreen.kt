package com.midas26.mobileapp.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green500

@Composable
fun PhoneVerificationScreen(
    phone: String,
    onBack: () -> Unit,
    onVerified: () -> Unit
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = AppColor.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.phone_verify_title),
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.phone_verify_subtitle, phone),
            style = MaterialTheme.typography.bodyLarge,
            color = AppColor.textTertiary,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        Spacer(modifier = Modifier.height(48.dp))

        OtpInputRow(
            code = code,
            onCodeChange = { code = it }
        )
        Spacer(modifier = Modifier.height(32.dp))

        AppPrimaryButton(
            text = stringResource(R.string.btn_verify),
            onClick = { if (code.length == 6) onVerified() },
            enabled = code.length == 6
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.phone_verify_resend),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary
            )
            TextButton(onClick = {
                Toast.makeText(context, context.getString(R.string.phone_verify_resent), Toast.LENGTH_SHORT).show()
            }) {
                Text(
                    text = stringResource(R.string.btn_resend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green500,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OtpInputRow(
    code: String,
    onCodeChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 숨겨진 입력 필드 — 실제 키보드 입력 처리
        BasicTextField(
            value = code,
            onValueChange = { new ->
                if (new.length <= 6 && new.all { it.isDigit() }) onCodeChange(new)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
        )

        // 시각적 6칸 박스
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(6) { i ->
                val char = code.getOrNull(i)
                val isFocused = i == code.length
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = if (char != null) Green50 else BrandWhite,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.5.dp,
                            color = when {
                                isFocused -> Green400
                                char != null -> Green400.copy(alpha = 0.5f)
                                else -> Gray200
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { focusRequester.requestFocus() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char?.toString() ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
