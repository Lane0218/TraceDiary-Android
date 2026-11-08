package com.tracediary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tracediary.app.ui.TraceDiaryApp
import com.tracediary.app.ui.theme.TraceDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as TraceDiaryApplication).restoreReminderOnUserLaunch()
        setContent {
            TraceDiaryTheme {
                TraceDiaryApp()
            }
        }
    }
}
