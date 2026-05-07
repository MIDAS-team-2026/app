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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.MidasWhite

@Composable
fun SignupCompleteScreen(
    userCode: String = "842716",
    onGoHome: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Green400)
    ) {
        // 흰 원 장식 (스플래시처럼)
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 230.dp, y = 80.dp)
                .clip(CircleShape)
                .background(MidasWhite.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 30.dp, y = 200.dp)
                .clip(CircleShape)
                .background(MidasWhite.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 🎉 원형 카드
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MidasWhite.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🎉", fontSize = 64.sp)
                }
            }
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.signup_complete_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MidasWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.signup_complete_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MidasWhite.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 코드 박스
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MidasWhite.copy(alpha = 0.16f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.signup_complete_code_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MidasWhite.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userCode.toCharArray().joinToString(" "),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MidasWhite
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.signup_complete_code_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MidasWhite.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MidasWhite.copy(alpha = 0.20f))
                            .clickable { copyToClipboard(context, userCode) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_copy_code),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MidasWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 하단 "홈으로 시작하기" 버튼 (흰 배경 + 녹색 텍스트)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .height(64.dp)
                .clickable(onClick = onGoHome),
            shape = RoundedCornerShape(16.dp),
            color = MidasWhite
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.btn_go_home),
                    style = MaterialTheme.typography.labelLarge,
                    color = Green600,
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
