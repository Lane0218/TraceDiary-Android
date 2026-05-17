package com.tracediary.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tracediary.app.ui.review.ReviewScreen
import com.tracediary.app.ui.settings.SettingsScreen
import com.tracediary.app.ui.stats.StatsScreen
import com.tracediary.app.ui.today.TodayScreen
import com.tracediary.app.ui.theme.TraceDiaryTheme

@Composable
fun TraceDiaryApp() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = appTabs()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        // 移除点击涟漪效果 (Hover)
                        interactionSource = remember { MutableInteractionSource() },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTabIndex) {
                0 -> TodayScreen()
                1 -> ReviewScreen()
                2 -> StatsScreen()
                else -> SettingsScreen()
            }
        }
    }
}

private fun appTabs(): List<AppTab> {
    return listOf(
        AppTab(label = "今天", icon = Icons.Outlined.WbSunny),
        AppTab(label = "回顾", icon = Icons.Outlined.AutoStories),
        AppTab(label = "统计", icon = Icons.Outlined.DonutLarge),
        AppTab(label = "设置", icon = Icons.Outlined.Settings)
    )
}

private data class AppTab(val label: String, val icon: ImageVector)
