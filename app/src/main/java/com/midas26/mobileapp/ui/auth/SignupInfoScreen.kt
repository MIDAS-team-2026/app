package com.midas26.mobileapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
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
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SignupInfoScreen(
    role: String,
    onBack: () -> Unit,
    onVerify: (phone: String) -> Unit,
    viewModel: AuthViewModel
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var birthError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()
    val isLoading = authState is AuthState.Loading

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.PhoneChecked -> {
                // 환자: 중복 없음 확인 + 인증코드 발송 완료 → 인증 화면으로
                viewModel.resetState()
                viewModel.savePendingSignupData(phone.trim(), password, name.trim(), role)
                onVerify(phone.trim())
            }
            is AuthState.Success -> {
                // 보호자: 회원가입 완료 → 인증 화면(완료 화면)으로
                val user = (authState as AuthState.Success).user
                val prefs = PrefsManager.from(context)
                prefs.saveToken(user.token.orEmpty())
                prefs.saveUserName(user.name.orEmpty())
                prefs.saveUserPhone(phone)
                prefs.saveUserRole(role)
                viewModel.resetState()
                onVerify(phone)
            }
            is AuthState.Error -> {
                val msg = (authState as AuthState.Error).message
                if (msg.contains("전화번호") || msg.contains("가입")) phoneError = msg
                else serverError = msg
            }
            else -> {}
        }
    }

    val errorRequired = stringResource(R.string.error_required)
    val errorPhoneInvalid = stringResource(R.string.error_phone_invalid)
    val errorPasswordShort = stringResource(R.string.error_password_short)
    val errorBirthFormat = stringResource(R.string.error_birth_format)
    val passwordHelper = stringResource(R.string.password_helper)

    fun validate(): Boolean {
        var ok = true
        nameError = if (name.trim().isEmpty()) { ok = false; errorRequired } else null
        birthError = when {
            birth.isEmpty() -> { ok = false; errorRequired }
            birth.length != 8 || birth.toLongOrNull() == null -> { ok = false; errorBirthFormat }
            else -> null
        }
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

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = AppColor.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .padding(end = 6.dp)
                    .background(Green400, RoundedCornerShape(3.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(Green400, RoundedCornerShape(3.dp))
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.signup_step_2_of_2),
            style = MaterialTheme.typography.labelMedium,
            color = AppColor.textTertiary
        )
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.signup_title_2),
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(40.dp))

        AppOutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null; serverError = null },
            label = stringResource(R.string.hint_name),
            leadingIcon = Icons.Filled.Person,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            errorText = nameError
        )
        AppOutlinedTextField(
            value = birth,
            onValueChange = { value -> birth = value.filter { it.isDigit() }; birthError = null },
            label = stringResource(R.string.hint_birth),
            leadingIcon = Icons.Filled.CalendarMonth,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
            maxLength = 8,
            errorText = birthError
        )
        AppOutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }; phoneError = null; serverError = null },
            label = stringResource(R.string.hint_phone),
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
            maxLength = 11,
            errorText = phoneError
        )
        AppOutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null; serverError = null },
            label = stringResource(R.string.hint_password),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            helperText = passwordHelper,
            errorText = passwordError
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
                text = stringResource(R.string.btn_complete),
                onClick = {
                    if (validate()) {
                        if (role == PrefsManager.ROLE_GUARDIAN) {
                            // 보호자: 즉시 회원가입 API 호출
                            viewModel.signup(phone.trim(), password, name.trim(), role)
                        } else {
                            // 환자: 중복 체크 + 인증코드 발송 → PhoneChecked 상태 되면 인증 화면으로
                            viewModel.checkPhoneAndSendCode(phone.trim())
                        }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
    VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
    }
}
