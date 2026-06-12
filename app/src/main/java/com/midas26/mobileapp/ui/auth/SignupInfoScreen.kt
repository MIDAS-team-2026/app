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
import androidx.compose.foundation.layout.imePadding
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
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SignupInfoScreen(
    role: String,
    onBack: () -> Unit,
    onVerify: (phone: String) -> Unit,
    viewModel: AuthViewModel
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(viewModel.pendingName) }
    var birth by remember { mutableStateOf(viewModel.pendingBirth) }
    var phone by remember { mutableStateOf(viewModel.pendingPhone) }
    var password by remember { mutableStateOf(viewModel.pendingPassword) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var birthError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordConfirmError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()
    val isLoading = authState is AuthState.Loading

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.PhoneChecked -> {
                viewModel.resetState()
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
    val errorPasswordMismatch = stringResource(R.string.error_password_mismatch)
    val errorBirthFormat = stringResource(R.string.error_birth_format)
    val passwordHelper = stringResource(R.string.password_helper)

    fun validate(): Boolean {
        var ok = true
        nameError = if (name.trim().isEmpty()) { ok = false; errorRequired } else null
        birthError = when {
            birth.isEmpty() -> { ok = false; errorRequired }
            birth.length != 8 || !birth.all { it.isDigit() } -> { ok = false; errorBirthFormat }
            else -> {
                val y = birth.substring(0, 4).toInt()
                val m = birth.substring(4, 6).toInt()
                val d = birth.substring(6, 8).toInt()
                val valid = y in 1900..2026
                    && m in 1..12
                    && d in 1..31
                    && birth.toLong() <= 20260609L
                    && runCatching {
                        java.util.Calendar.getInstance().apply {
                            isLenient = false
                            set(y, m - 1, d)
                            time
                        }
                    }.isSuccess
                if (!valid) { ok = false; errorBirthFormat } else null
            }
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
        passwordConfirmError = when {
            passwordConfirm.isEmpty() -> { ok = false; errorRequired }
            passwordConfirm != password -> { ok = false; errorPasswordMismatch }
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
            .imePadding()
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
                    .background(AppColor.greenPrimary, RoundedCornerShape(3.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .padding(end = 6.dp)
                    .background(AppColor.greenPrimary, RoundedCornerShape(3.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(AppColor.divider, RoundedCornerShape(3.dp))
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.signup_step_2_of_3),
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
            onValueChange = { input ->
                val maxLen = if (input.any { it in '가'..'힣' || it in '㄰'..'㆏' }) 7 else 15
                if (input.length <= maxLen) { name = input; nameError = null; serverError = null }
            },
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
            onValueChange = { password = it; passwordError = null; passwordConfirmError = null; serverError = null },
            label = stringResource(R.string.hint_password),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Next,
            helperText = passwordHelper,
            errorText = passwordError
        )
        AppOutlinedTextField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it; passwordConfirmError = null },
            label = stringResource(R.string.hint_password_confirm),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            errorText = passwordConfirmError
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
                CircularProgressIndicator(color = AppColor.greenSecondary)
            }
        } else {
            AppPrimaryButton(
                text = stringResource(R.string.btn_complete),
                onClick = {
                    if (validate()) {
                        viewModel.savePendingSignupData(phone.trim(), password, name.trim(), role, birth.trim())
                        onVerify(phone.trim())
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
    VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
    }
}
