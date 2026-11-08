package com.tracediary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tracediary.app.ui.TraceDiaryApp
import com.tracediary.app.ui.theme.TraceDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val traceDiaryApplication = application as TraceDiaryApplication
        traceDiaryApplication.restoreReminderOnUserLaunch()
        val preferences = traceDiaryApplication.userPreferencesRepository.preferences
        lifecycleScope.launch {
            val initialPreferences = preferences.first()
            setContent {
                val currentPreferences by preferences.collectAsStateWithLifecycle(initialValue = initialPreferences)
                TraceDiaryTheme(themeColor = currentPreferences.themeColor) {
                    TraceDiaryApp()
                }
            }
        }
    }
}
