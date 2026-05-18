package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite

/**
 * 보호자에게 공유하기 — Material3 ModalBottomSheet.
 * AnalysisResult/Graph 어디서든 호출 가능.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToGuardianSheet(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Gray200)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "보호자에게 공유",
                style = MaterialTheme.typography.headlineSmall,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "오늘의 분석 결과를 전달할까요?",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 보호자 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Green50
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BrandWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = viewModel.guardian.icon,
                            contentDescription = null,
                            tint = Green600,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.guardian.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Gray800,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = viewModel.guardian.relationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Green600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Green600,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 공유 옵션 토글
            viewModel.shareOptions.forEachIndexed { idx, opt ->
                ShareOptionRow(
                    option = opt,
                    selected = viewModel.shareSelected[idx],
                    onToggle = { viewModel.toggleShareOption(idx) }
                )
                if (idx < viewModel.shareOptions.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 하단 버튼: 취소 / 공유하기
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(16.dp),
                    color = BrandWhite,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Gray200)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "취소",
                            style = MaterialTheme.typography.labelLarge,
                            color = Gray400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box(modifier = Modifier.weight(1.4f)) {
                    AppPrimaryButton(text = "공유하기", onClick = onShare)
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    option: ShareOption,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Green50 else BrandWhite,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.5.dp,
            color = if (selected) Green400 else Gray200
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 체크박스
            Surface(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Green400 else BrandWhite,
                border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) Green400 else Gray200)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = BrandWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Gray800,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }
    }
}
