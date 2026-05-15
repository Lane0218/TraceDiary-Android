package com.tracediary.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tracediary.app.ui.review.ReviewScreen
import com.tracediary.app.ui.settings.SettingsScreen
import com.tracediary.app.ui.stats.StatsScreen
import com.tracediary.app.ui.today.TodayScreen
import com.tracediary.app.ui.theme.Ink500
import com.tracediary.app.ui.theme.SurfaceCard
import com.tracediary.app.ui.theme.TraceDiaryTheme

@Composable
fun TraceDiaryApp() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = appTabs()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(text = item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Ink500,
                            unselectedTextColor = Ink500,
                            indicatorColor = MaterialTheme.colorScheme.surface
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
        AppTab(label = "今天", icon = Icons.Outlined.CalendarMonth),
        AppTab(label = "回顾", icon = Icons.Outlined.MenuBook),
        AppTab(label = "统计", icon = Icons.Outlined.BarChart),
        AppTab(label = "设置", icon = Icons.Outlined.Settings)
    )
}

private data class AppTab(
    val label: String,
    val icon: ImageVector
)

@Preview(showBackground = true)
@Composable
private fun TraceDiaryAppPreview() {
    TraceDiaryTheme {
        TraceDiaryApp()
    }
}
