package com.midas26.mobileapp.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun SignupCompleteScreen(
    viewModel: AuthViewModel,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    var userCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 화면 최초 진입 시 회원가입 API 호출
    LaunchedEffect(Unit) {
        viewModel.signupFromPending()
    }

    // 회원가입 결과 처리
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                val user = state.user
                val prefs = PrefsManager.from(context)
                prefs.saveToken(user.token.orEmpty())
                prefs.saveUserName(user.name.orEmpty())
                prefs.saveUserPhone(viewModel.pendingPhone)
                prefs.saveUserRole(viewModel.pendingRole.ifEmpty { PrefsManager.ROLE_USER })
                prefs.saveUserId(user.userId ?: -1)
                prefs.saveUserCode(user.patientCode.orEmpty())
                userCode = user.patientCode.orEmpty().ifEmpty { "------" }
                viewModel.resetState()
            }
            is AuthState.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

    val isLoading = authState is AuthState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Green400)
    ) {
        // 흰 원 장식
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 230.dp, y = 80.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 30.dp, y = 200.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = BrandWhite.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        tint = BrandWhite,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.signup_complete_title),
                style = MaterialTheme.typography.headlineSmall,
                color = BrandWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.signup_complete_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandWhite.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 코드 박스
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite.copy(alpha = 0.16f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.signup_complete_code_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        isLoading -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            CircularProgressIndicator(
                                color = BrandWhite,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        errorMessage != null -> {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandWhite.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandWhite.copy(alpha = 0.20f))
                                    .clickable {
                                        errorMessage = null
                                        viewModel.signupFromPending()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "다시 시도",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BrandWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> {
                            // 8자리 hex → 4+4 두 묶음으로 표시
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = userCode.take(4),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandWhite,
                                    letterSpacing = 4.sp
                                )
                                Text(
                                    text = "·",
                                    fontSize = 28.sp,
                                    color = BrandWhite.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = userCode.drop(4),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandWhite,
                                    letterSpacing = 4.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.signup_complete_code_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandWhite.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandWhite.copy(alpha = 0.20f))
                                    .clickable { copyToClipboard(context, userCode) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_copy_code),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BrandWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 하단 "홈으로 시작하기" 버튼 — 코드가 발급된 후에만 활성화
        val homeButtonEnabled = userCode.isNotEmpty()
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .height(64.dp)
                .clickable(enabled = homeButtonEnabled, onClick = onGoHome),
            shape = RoundedCornerShape(16.dp),
            color = if (homeButtonEnabled) BrandWhite else BrandWhite.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.btn_go_home),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (homeButtonEnabled) AppColor.accentDark else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("midas user code", text))
    Toast.makeText(context, "코드가 복사되었어요", Toast.LENGTH_SHORT).show()
}
