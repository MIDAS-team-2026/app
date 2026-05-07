package com.midas26.mobileapp.ui.legal

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.MidasPrimaryButton
import com.midas26.mobileapp.ui.theme.Amber400
import com.midas26.mobileapp.ui.theme.Amber50
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green100
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.MidasWhite

private data class PrivacyItem(
    val titleRes: Int,
    val descRes: Int,
    val isRequired: Boolean
)

@Composable
fun PrivacyScreen(
    onAgreeAndStart: () -> Unit
) {
    val items = listOf(
        PrivacyItem(R.string.privacy_terms, R.string.privacy_terms_desc, true),
        PrivacyItem(R.string.privacy_personal, R.string.privacy_personal_desc, true),
        PrivacyItem(R.string.privacy_voice, R.string.privacy_voice_desc, true),
        PrivacyItem(R.string.privacy_marketing, R.string.privacy_marketing_desc, false)
    )

    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(items.size) { this.add(false) } } }
    val allChecked by remember { derivedStateOf { checked.all { it } } }
    val canProceed by remember {
        derivedStateOf {
            items.indices.all { idx -> !items[idx].isRequired || checked[idx] }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 상단 연녹색 헤더 + 더 연한 원 장식
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Green50)
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .offset(x = 270.dp, y = 30.dp)
                    .clip(CircleShape)
                    .background(Green100.copy(alpha = 0.7f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-44).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 자물쇠+키 아이콘 카드 (녹색 배경)
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(22.dp),
                color = Green400,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🔐", fontSize = 50.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(28.dp))

            // 전체 동의 마스터
            AgreeAllCard(
                checked = allChecked,
                onToggle = {
                    val nextValue = !allChecked
                    for (i in checked.indices) checked[i] = nextValue
                }
            )
            Spacer(modifier = Modifier.height(20.dp))

            items.forEachIndexed { idx, item ->
                PrivacyRow(
                    item = item,
                    checked = checked[idx],
                    onToggle = { checked[idx] = !checked[idx] }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            MidasPrimaryButton(
                text = stringResource(R.string.btn_agree_and_start),
                onClick = onAgreeAndStart,
                enabled = canProceed
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AgreeAllCard(
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = Green50,
        border = BorderStroke(2.5.dp, Green400)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, accent = Green400)
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.privacy_agree_all),
                    style = MaterialTheme.typography.titleMedium,
                    color = Green600,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.privacy_agree_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Green500
                )
            }
        }
    }
}

@Composable
private fun PrivacyRow(
    item: PrivacyItem,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = MidasWhite,
        border = BorderStroke(1.5.dp, Gray200)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, accent = if (item.isRequired) Green400 else Amber400)
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = Gray800,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.isRequired) Green50 else Amber50)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (item.isRequired) R.string.permission_required
                        else R.string.permission_optional
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.isRequired) Green400 else Amber400,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun Checkbox(
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) accent else MidasWhite,
        border = BorderStroke(2.dp, if (checked) accent else Gray200)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MidasWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

