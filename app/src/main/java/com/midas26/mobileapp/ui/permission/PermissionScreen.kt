package com.midas26.mobileapp.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Amber400
import com.midas26.mobileapp.ui.theme.Amber50
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite

private data class PermissionItem(
    val emoji: String,
    val titleRes: Int,
    val descRes: Int,
    val isRequired: Boolean
)

@Composable
fun PermissionScreen(
    onNext: () -> Unit
) {
    val items = listOf(
        PermissionItem("🎙️", R.string.permission_mic, R.string.permission_mic_desc, true),
        PermissionItem("📍", R.string.permission_location, R.string.permission_location_desc, false),
        PermissionItem("🔔", R.string.permission_notification, R.string.permission_notification_desc, false),
        PermissionItem("📂", R.string.permission_storage, R.string.permission_storage_desc, false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 상단 녹색 헤더 + 흰 원 장식 (190 → 150dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Green400)
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = 250.dp, y = 20.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.18f))
            )
        }

        // 자물쇠 아이콘 카드 + 본문
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-40).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 자물쇠 카드 96 → 80dp
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🔒", fontSize = 44.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permission_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 권한 카드 4개 — 사이 간격 12 → 8
            items.forEachIndexed { idx, item ->
                PermissionRow(item = item)
                if (idx < items.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }

            // 남는 공간을 weight 로 흡수해 버튼이 하단 근처에 위치
            Spacer(modifier = Modifier.weight(1f))
            AppPrimaryButton(
                text = stringResource(R.string.btn_grant_and_start),
                onClick = onNext
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionRow(item: PermissionItem) {
    // 필수=녹색, 선택=앰버 (위치/알림/저장소 모두 앰버로 통일하여 시각적 잡음 최소화)
    val cardBg = if (item.isRequired) Green50 else Amber50
    val stroke = if (item.isRequired) Green400 else Amber400
    val chipBg = cardBg
    val chipColor = stroke
    val chipText = if (item.isRequired) R.string.permission_required else R.string.permission_optional

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(2.dp, stroke)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 좌측 흰 박스 + 이모지 — 64→52dp, 이모지 36→30sp
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                color = BrandWhite
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = item.emoji, fontSize = 30.sp)
                }
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = Gray800,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
            // 우측 칩
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(chipBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(chipText),
                    style = MaterialTheme.typography.labelMedium,
                    color = chipColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

