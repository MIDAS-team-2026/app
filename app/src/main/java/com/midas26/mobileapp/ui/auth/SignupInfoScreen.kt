package com.midas26.mobileapp.ui.auth

import android.util.Patterns
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.midas26.mobileapp.ui.components.MidasOutlinedTextField
import com.midas26.mobileapp.ui.components.MidasPrimaryButton
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SignupInfoScreen(
    role: String,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var birthError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val errorRequired = stringResource(R.string.error_required)
    val errorEmailInvalid = stringResource(R.string.error_email_invalid)
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
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Gray800,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 진행도 (2/2 단계: 모두 채워짐) — 두께 4dp→6dp 로 확대
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
            color = Gray400
        )
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.signup_title_2),
            style = MaterialTheme.typography.headlineSmall,
            color = Gray800,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(40.dp))

        MidasOutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = stringResource(R.string.hint_name),
            leadingIcon = Icons.Filled.Person,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            errorText = nameError
        )
        MidasOutlinedTextField(
            value = birth,
            onValueChange = { value -> birth = value.filter { it.isDigit() }; birthError = null },
            label = stringResource(R.string.hint_birth),
            leadingIcon = Icons.Filled.CalendarMonth,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
            maxLength = 8,
            errorText = birthError
        )
        MidasOutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = stringResource(R.string.hint_email),
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            errorText = emailError
        )
        MidasOutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = stringResource(R.string.hint_password),
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            helperText = passwordHelper,
            errorText = passwordError
        )

        Spacer(modifier = Modifier.height(24.dp))

        MidasPrimaryButton(
            text = stringResource(R.string.btn_complete),
            onClick = {
                if (validate()) {
                    // TODO: ViewModel로 회원가입 요청 연결 (role, name, birth, email, password)
                    PrefsManager.from(context).saveUserRole(role)
                    onComplete()
                }
            }
        )
        Spacer(modifier = Modifier.height(48.dp))
    }
}
