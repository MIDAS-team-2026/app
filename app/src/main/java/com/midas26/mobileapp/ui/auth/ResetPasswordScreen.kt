package com.midas26.mobileapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.Green500

@Composable
fun ResetPasswordScreen(
    phone: String,
    onBack: () -> Unit,
    onPasswordReset: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val state by viewModel.state.collectAsState()
    val isLoading = state is ForgotPasswordState.Loading

    LaunchedEffect(state) {
        when (state) {
            is ForgotPasswordState.PasswordReset -> {
                viewModel.resetState()
                onPasswordReset()
            }
            is ForgotPasswordState.Error -> {
                serverError = (state as ForgotPasswordState.Error).message
            }
            else -> {}
        }
    }

    fun validate(): Boolean {
        var ok = true
        newPasswordError = when {
            newPassword.isEmpty() -> { ok = false; "새 비밀번호를 입력해주세요." }
            newPassword.length < 8 -> { ok = false; "비밀번호는 8자 이상이어야 합니다." }
            else -> null
        }
        confirmPasswordError = when {
            confirmPassword.isEmpty() -> { ok = false; "비밀번호 확인을 입력해주세요." }
            confirmPassword != newPassword -> { ok = false; "비밀번호가 일치하지 않습니다." }
            else -> null
        }
        return ok
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
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
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "새 비밀번호 설정",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "사용할 새 비밀번호를 입력해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(40.dp))

        AppOutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                newPasswordError = null
                serverError = null
            },
            label = "새 비밀번호",
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Next,
            errorText = newPasswordError
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppOutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmPasswordError = null
                serverError = null
            },
            label = "비밀번호 확인",
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            errorText = confirmPasswordError
        )

        if (serverError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = serverError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green500)
            }
        } else {
            AppPrimaryButton(
                text = "비밀번호 변경",
                onClick = {
                    if (validate()) {
                        serverError = null
                        viewModel.resetPassword(phone, newPassword)
                    }
                }
            )
        }
    }
}
