package com.midas26.mobileapp.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.components.AppTextButton
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()
    val isLoading = authState is AuthState.Loading

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                val user = (authState as AuthState.Success).user
                val prefs = PrefsManager.from(context)
                prefs.saveToken(user.token.orEmpty())
                prefs.saveUserId(user.userId ?: -1)
                prefs.saveUserName(user.name.orEmpty())
                prefs.saveUserPhone(user.phone.orEmpty())
                prefs.saveUserRole(user.role.orEmpty())
                viewModel.resetState()
                onNavigateToHome()
            }
            is AuthState.Error -> {
                serverError = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }

    val errorRequired = stringResource(R.string.error_required)
    val errorPhoneInvalid = stringResource(R.string.error_phone_invalid)
    val errorPasswordShort = stringResource(R.string.error_password_short)

    fun validate(): Boolean {
        var ok = true
        phoneError = when {
            phone.trim().isEmpty() -> { ok = false; errorRequired }
            !phone.trim().matches(Regex("^01[0-9]{8,9}$")) -> { ok = false; errorPhoneInvalid }
            else -> null
        }
        passwordError = when {
            password.isEmpty() -> { ok = false; errorRequired }
            password.length < 8 -> { ok = false; errorPasswordShort }
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
        Spacer(modifier = Modifier.height(64.dp))

        // 앱 아이콘 (런처 아이콘과 동일한 디자인)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF4CAF50), Color(0xFF2D7D31))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(40.dp))

        // 입력 폼
        AppOutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it.filter { c -> c.isDigit() }
                phoneError = null
                serverError = null
            },
            label = stringResource(R.string.hint_phone),
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
            maxLength = 11,
            errorText = phoneError
        )
        AppOutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                serverError = null
            },
            label = stringResource(R.string.hint_password),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            errorText = passwordError
        )

        // 서버 에러 메시지
        if (serverError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = serverError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // 비밀번호 찾기
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            AppTextButton(
                text = stringResource(R.string.btn_forgot_password),
                onClick = onForgotPassword
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 로그인 버튼
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green500)
            }
        } else {
            AppPrimaryButton(
                text = stringResource(R.string.btn_login),
                onClick = {
                    if (validate()) {
                        viewModel.login(phone.trim(), password)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        // 구분선
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AppColor.divider)
            )
            Text(
                text = stringResource(R.string.login_or),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AppColor.divider)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 회원가입 유도
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.login_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary
            )
            AppTextButton(
                text = stringResource(R.string.btn_signup),
                onClick = onNavigateToSignup
            ) {
                Text(
                    text = stringResource(R.string.btn_signup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green500,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
