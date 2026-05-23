package com.tracediary.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.security.AppLockState
import com.tracediary.app.ui.review.ReviewScreen
import com.tracediary.app.ui.security.AppLockScreen
import com.tracediary.app.ui.security.AppLockViewModel
import com.tracediary.app.ui.settings.SettingsScreen
import com.tracediary.app.ui.stats.StatsScreen
import com.tracediary.app.ui.today.TodayScreen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceDiaryApp() {
    val application = LocalContext.current.applicationContext as TraceDiaryApplication
    val lockViewModel: AppLockViewModel = viewModel(factory = AppLockViewModel.factory(application))
    val lockState by lockViewModel.lockState.collectAsStateWithLifecycle()
    val lockUi by lockViewModel.uiState.collectAsStateWithLifecycle()

    if (lockState != AppLockState.Unlocked) {
        AppLockScreen(
            state = lockState,
            rememberPassword = lockUi.rememberPassword,
            errorMessage = lockUi.errorMessage,
            onRememberChanged = lockViewModel::onToggleRemember,
            onSetupPassword = lockViewModel::onSetupPassword,
            onUnlock = lockViewModel::onUnlock,
        )
        return
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    // LocalDate 不能直接 Saveable，这里以 epochDay 形式持久化；-1 视为无值。
    var pendingTodayEpochDay by rememberSaveable { mutableStateOf(Long.MIN_VALUE) }
    val pendingTodayDate: LocalDate? = remember(pendingTodayEpochDay) {
        if (pendingTodayEpochDay == Long.MIN_VALUE) null else LocalDate.ofEpochDay(pendingTodayEpochDay)
    }
    val tabs = appTabs()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 在 Material 3 中，使用 LocalRippleConfiguration 禁用涟漪
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
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
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTabIndex) {
                0 -> TodayScreen(
                    pendingDate = pendingTodayDate,
                    onPendingDateConsumed = { pendingTodayEpochDay = Long.MIN_VALUE },
                    onOpenSettings = { selectedTabIndex = 3 },
                )
                1 -> ReviewScreen(
                    onJumpToToday = { date ->
                        pendingTodayEpochDay = date.toEpochDay()
                        selectedTabIndex = 0
                    },
                )
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
