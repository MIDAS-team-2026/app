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
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

private data class PrivacyItem(
    val titleRes: Int,
    val descRes: Int,
    val detailTitle: String,
    val detailContent: String
)

@Composable
fun PrivacyScreen(
    onAgreeAndStart: () -> Unit
) {
    // 약관 본문은 LegalTermsContent.kt 한 곳에서 관리한다. (설정 화면의 약관 보기와 같은 내용)
    val items = listOf(
        PrivacyItem(
            titleRes = R.string.privacy_terms,
            descRes = R.string.privacy_terms_desc,
            detailTitle = ServiceTermsItem.detailTitle,
            detailContent = ServiceTermsItem.detailContent
        ),
        PrivacyItem(
            titleRes = R.string.privacy_personal,
            descRes = R.string.privacy_personal_desc,
            detailTitle = PrivacyPolicyItem.detailTitle,
            detailContent = PrivacyPolicyItem.detailContent
        ),
        PrivacyItem(
            titleRes = R.string.privacy_voice,
            descRes = R.string.privacy_voice_desc,
            detailTitle = VoiceDataTermsItem.detailTitle,
            detailContent = VoiceDataTermsItem.detailContent
        )
    )

    val checked = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(items.size) { add(false) }
        }
    }

    var selectedItem by remember { mutableStateOf<PrivacyItem?>(null) }

    val allChecked by remember {
        derivedStateOf { checked.all { it } }
    }

    val canProceed by remember {
        derivedStateOf { checked.all { it } }
    }

    selectedItem?.let { item ->
        TermsDetailDialog(
            title = item.detailTitle,
            content = item.detailContent,
            onDismiss = { selectedItem = null }
        )
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(AppColor.greenSurface)
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .offset(x = 270.dp, y = 30.dp)
                        .clip(CircleShape)
                        .background(AppColor.greenSurfaceVariant.copy(alpha = 0.7f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-44).dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = AppColor.greenPrimary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.privacy_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "필수 약관을 확인하고 동의해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary
                )

                Spacer(modifier = Modifier.height(28.dp))

                AgreeAllCard(
                    checked = allChecked,
                    onToggle = {
                        val nextValue = !allChecked
                        for (i in checked.indices) {
                            checked[i] = nextValue
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                items.forEachIndexed { idx, item ->
                    PrivacyRow(
                        item = item,
                        checked = checked[idx],
                        onToggle = { checked[idx] = !checked[idx] },
                        onDetailClick = { selectedItem = item }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                AppPrimaryButton(
                    text = stringResource(R.string.btn_agree_and_start),
                    onClick = onAgreeAndStart,
                    enabled = canProceed
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        VerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun TermsDetailDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "확인",
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
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
        color = AppColor.greenSurface,
        border = BorderStroke(2.5.dp, AppColor.greenPrimary)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                accent = AppColor.greenPrimary
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Text(
                    text = stringResource(R.string.privacy_agree_all),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.accentDark,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.privacy_agree_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.greenSecondary
                )
            }
        }
    }
}

@Composable
private fun PrivacyRow(
    item: PrivacyItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onDetailClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                accent = AppColor.greenPrimary,
                modifier = Modifier.clickable(onClick = onToggle)
            )

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColor.greenSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.size(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "자세히 보기",
                tint = AppColor.textTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun Checkbox(
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) accent else BrandWhite,
        border = BorderStroke(2.dp, if (checked) accent else AppColor.divider)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}