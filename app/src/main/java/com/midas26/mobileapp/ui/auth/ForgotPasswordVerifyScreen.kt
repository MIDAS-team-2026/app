package com.midas26.mobileapp.ui.auth

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Red400

@Composable
fun ForgotPasswordVerifyScreen(
    phone: String,
    onBack: () -> Unit,
    onVerified: (phone: String) -> Unit,
    viewModel: PhoneVerificationViewModel = viewModel()
) {
    var code by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsState()
    val isLoading = state is VerificationState.Sending || state is VerificationState.Verifying

    // 화면 진입 시 인증번호 발송
    LaunchedEffect(Unit) {
        viewModel.sendCode(phone)
    }

    LaunchedEffect(state) {
        when (state) {
            is VerificationState.Verified -> {
                viewModel.resetState()
                onVerified(phone)
            }
            is VerificationState.Error -> {
                errorMsg = (state as VerificationState.Error).message
            }
            else -> {}
        }
    }

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
            text = "인증코드 입력",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "6자리 인증코드를 입력해주세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(48.dp))

        OtpRow(code = code, onCodeChange = { code = it; errorMsg = null })
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = Red400,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        AppPrimaryButton(
            text = if (isLoading) "" else "확인",
            onClick = { if (code.length == 6) viewModel.verifyCode(phone, code) },
            enabled = code.length == 6 && !isLoading
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun OtpRow(code: String, onCodeChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = code,
            onValueChange = { new ->
                if (new.length <= 6 && new.all { it.isDigit() }) onCodeChange(new)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.size(1.dp).focusRequester(focusRequester)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
