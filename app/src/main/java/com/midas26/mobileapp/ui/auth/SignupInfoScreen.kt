package com.midas26.mobileapp.ui.auth

import android.util.Patterns
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SignupInfoScreen(
    role: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: AuthViewModel = viewModel()
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
    var serverError by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()
    val isLoading = authState is AuthState.Loading

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                viewModel.resetState()
                PrefsManager.from(context).saveUserRole(role)
                onComplete()
            }
            is AuthState.Error -> {
                serverError = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }

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
            value = email,
            onValueChange = { email = it; emailError = null; serverError = null },
            label = stringResource(R.string.hint_email),
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            errorText = emailError
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
                        viewModel.signup(email.trim(), password, name.trim(), role)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}
