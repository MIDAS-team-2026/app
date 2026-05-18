package com.midas26.mobileapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400

sealed interface TabId
enum class UserHomeTab : TabId { Home, Chat, Analysis, Settings }
enum class GuardianHomeTab : TabId { Home, Analysis, Location, Settings }

data class TabItem(val id: TabId, val icon: ImageVector, val labelRes: Int)

val userTabs = listOf(
    TabItem(UserHomeTab.Home,     Icons.Default.Home,       R.string.tab_home),
    TabItem(UserHomeTab.Chat,     Icons.Default.Mic,        R.string.tab_chat),
    TabItem(UserHomeTab.Analysis, Icons.AutoMirrored.Filled.TrendingUp, R.string.tab_analysis),
    TabItem(UserHomeTab.Settings, Icons.Default.Settings,   R.string.menu_settings)
)

val guardianTabs = listOf(
    TabItem(GuardianHomeTab.Home,     Icons.Default.Home,       R.string.tab_home),
    TabItem(GuardianHomeTab.Analysis, Icons.Default.BarChart,   R.string.tab_analysis),
    TabItem(GuardianHomeTab.Location, Icons.Default.LocationOn, R.string.guardian_tab_location),
    TabItem(GuardianHomeTab.Settings, Icons.Default.Settings,   R.string.menu_settings)
)

@Composable
fun AppBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<TabItem>,
    selectedTab: TabId,
    onTabClick: (TabId) -> Unit,
    accent: Color
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = Gray200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !selected) { onTabClick(tab.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) accent else Gray400,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) accent else Gray400,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
