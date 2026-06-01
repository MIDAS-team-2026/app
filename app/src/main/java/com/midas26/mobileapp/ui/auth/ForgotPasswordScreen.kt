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
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.Green500

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onCodeSent: (phone: String) -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    var phone by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val state by viewModel.state.collectAsState()
    val isLoading = state is ForgotPasswordState.Loading

    LaunchedEffect(state) {
        when (state) {
            is ForgotPasswordState.PhoneVerified -> {
                viewModel.resetState()
                onCodeSent(phone.trim())
            }
            is ForgotPasswordState.Error -> {
                serverError = (state as ForgotPasswordState.Error).message
            }
            else -> {}
        }
    }

    fun validate(): Boolean {
        phoneError = when {
            phone.trim().isEmpty() -> "전화번호를 입력해주세요."
            !phone.trim().matches(Regex("^01[0-9]{8,9}$")) -> "올바른 전화번호를 입력해주세요."
            else -> null
        }
        return phoneError == null
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
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
            text = "비밀번호 찾기",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "가입한 전화번호를 입력해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(40.dp))

        AppOutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it.filter { c -> c.isDigit() }
                phoneError = null
                serverError = null
            },
            label = "전화번호",
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            maxLength = 11,
            errorText = phoneError
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
                text = "다음",
                onClick = { if (validate()) viewModel.checkPhone(phone.trim()) }
            )
        }
    }
    VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
    }
}
