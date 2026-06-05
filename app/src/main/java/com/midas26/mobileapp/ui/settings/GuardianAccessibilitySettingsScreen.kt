package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.FontSizeLevel
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray600
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.GuardianAccent
import com.midas26.mobileapp.ui.theme.GuardianAccentDark
import com.midas26.mobileapp.ui.theme.LocalHighContrast
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun GuardianAccessibilitySettingsScreen(
    onBack: () -> Unit = {},
    onFontSizeChange: (FontSizeLevel) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onHapticChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)

    var fontSizeLevel by remember { mutableIntStateOf(prefs.getAccessibilityFontSize()) }
    var highContrast by remember { mutableStateOf(prefs.getHighContrast()) }
    var hapticFeedback by remember { mutableStateOf(prefs.getHapticFeedback()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        GuardianAccessibilityTopBar(onBack = onBack)

        val scrollState = rememberScrollState()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                GuardianAccessibilitySection(title = "글씨 크기") {
                    GuardianFontSizeSelector(
                        selectedLevel = fontSizeLevel,
                        onSelect = { level ->
                            fontSizeLevel = level
                            prefs.setAccessibilityFontSize(level)
                            onFontSizeChange(FontSizeLevel.fromIndex(level))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GuardianAccessibilitySection(title = "화면 표시") {
                    GuardianAccessibilityToggleRow(
                        label = "고대비 모드",
                        description = "텍스트와 배경의 대비를 높여 더 잘 보이게 해요",
                        checked = highContrast,
                        onCheckedChange = {
                            highContrast = it
                            prefs.setHighContrast(it)
                            onHighContrastChange(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GuardianAccessibilitySection(title = "피드백") {
                    GuardianAccessibilityToggleRow(
                        label = "진동 피드백",
                        description = "버튼을 누를 때 진동으로 알려줘요",
                        checked = hapticFeedback,
                        onCheckedChange = {
                            hapticFeedback = it
                            prefs.setHapticFeedback(it)
                            onHapticChange(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun GuardianAccessibilityTopBar(onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 12.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary
                )
            }

            Text(
                text = "접근성 설정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }
    }
}

@Composable
private fun GuardianAccessibilitySection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BrandWhite,
            shadowElevation = AppColor.cardShadowElevation,
            border = AppColor.cardBorder
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun GuardianFontSizeSelector(
    selectedLevel: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf("작게", "보통", "크게", "매우\n크게")
    val previewSizes = listOf(14.sp, 17.sp, 21.sp, 26.sp)
    val highContrast = LocalHighContrast.current
    val unselectedBorder = if (highContrast) Gray600 else Gray200

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = selectedLevel == index

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GuardianAccentDark else unselectedBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                GuardianAccent.copy(alpha = 0.16f)
                            } else {
                                BrandWhite
                            }
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "가",
                            fontSize = previewSizes[index],
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) GuardianAccentDark else Gray600
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = label,
                            fontSize = previewSizes[index],
                            color = if (isSelected) GuardianAccentDark else Gray400,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianAccessibilityToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColor.textTertiary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandWhite,
                checkedTrackColor = GuardianAccentDark,
                uncheckedThumbColor = BrandWhite,
                uncheckedTrackColor = Gray200
            )
        )
    }
}