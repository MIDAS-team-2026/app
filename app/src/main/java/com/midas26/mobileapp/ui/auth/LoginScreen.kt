package com.midas26.mobileapp.ui.auth

import android.util.Patterns
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.components.AppTextButton
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onForgotPassword: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val errorRequired = stringResource(R.string.error_required)
    val errorEmailInvalid = stringResource(R.string.error_email_invalid)
    val errorPasswordShort = stringResource(R.string.error_password_short)

    fun validate(): Boolean {
        var ok = true
        emailError = when {
            email.trim().isEmpty() -> { ok = false; errorRequired }
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> { ok = false; errorEmailInvalid }
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

        // 앱 아이콘 (56→72dp)
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = Green400,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Gray800,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Gray400
        )
        Spacer(modifier = Modifier.height(40.dp))

        // 입력 폼
        AppOutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = stringResource(R.string.hint_email),
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            errorText = emailError
        )
        AppOutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = stringResource(R.string.hint_password),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            errorText = passwordError
        )

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
        AppPrimaryButton(
            text = stringResource(R.string.btn_login),
            onClick = {
                if (validate()) {
                    // TODO: ViewModel로 로그인 요청 연결
                    onNavigateToHome()
                }
            }
        )
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
                    .background(Gray200)
            )
            Text(
                text = stringResource(R.string.login_or),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Gray200)
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
                color = Gray400
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
