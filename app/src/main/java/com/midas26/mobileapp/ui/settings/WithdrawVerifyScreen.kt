package com.midas26.mobileapp.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun WithdrawVerifyScreen(
    phone: String,
    onBack: () -> Unit,
    onWithdrawn: () -> Unit,
    viewModel: WithdrawViewModel = viewModel()
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val isLoading = state is WithdrawState.Loading || state is WithdrawState.Sending

    // 화면 진입 시 인증번호 발송
    LaunchedEffect(Unit) {
        viewModel.sendCode(phone)
    }

    LaunchedEffect(state) {
        when (state) {
            is WithdrawState.Withdrawn -> {
                PrefsManager.from(context).clearToken()
                viewModel.resetState()
                onWithdrawn()
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "6자리 인증코드를 입력해주세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColor.textTertiary
        )

        Spacer(modifier = Modifier.height(48.dp))

        WithdrawOtpRow(code = code, onCodeChange = { code = it })

        // 에러 메시지
        if (state is WithdrawState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (state as WithdrawState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColor.errorPrimary)
            }
        } else {
            Button(
                onClick = { if (code.length == 6) showConfirmDialog = true },
                enabled = code.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColor.errorPrimary,
                    disabledContainerColor = AppColor.errorPrimary.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = "탈퇴 확인",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandWhite
                )
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = {
                    Text(
                        text = "정말 탈퇴하시겠어요?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "탈퇴 후 모든 데이터는 영구적으로 삭제되며,\n절대 복구할 수 없습니다.\n그래도 진행하시겠어요?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColor.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog = false
                        viewModel.withdraw(phone)
                    }) {
                        Text(
                            text = "탈퇴하기",
                            color = AppColor.errorPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(text = "취소", color = AppColor.textTertiary)
                    }
                },
                containerColor = BrandWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WithdrawOtpRow(code: String, onCodeChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) { i ->
                val char = code.getOrNull(i)
                val isFocused = i == code.length
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = if (char != null) AppColor.errorPrimary.copy(alpha = 0.08f) else BrandWhite,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.5.dp,
                            color = when {
                                isFocused -> AppColor.errorPrimary
                                char != null -> AppColor.errorPrimary.copy(alpha = 0.5f)
                                else -> AppColor.divider
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
