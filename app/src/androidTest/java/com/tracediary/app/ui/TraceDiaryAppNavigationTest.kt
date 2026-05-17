package com.tracediary.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.ui.theme.TraceDiaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 TraceDiaryApp 底部 NavigationBar 4 个 Tab 文案存在，
 * 并且切到「回顾」Tab 后能看到日历分段按钮文案，对应 SPEC 6.1.4 主导航最小回归。
 */
@RunWith(AndroidJUnit4::class)
class TraceDiaryAppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `底部导航 4 个 Tab 文案存在`() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TraceDiaryApp()
                }
            }
        }

        composeTestRule.waitForIdle()

        // 这些文案都来自底部 NavigationBar 的 label，必然存在
        composeTestRule.onNodeWithText("今天").assertIsDisplayed()
        composeTestRule.onNodeWithText("回顾").assertIsDisplayed()
        composeTestRule.onNodeWithText("统计").assertIsDisplayed()
        composeTestRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun `点击回顾 Tab 后能看到日历分段按钮`() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TraceDiaryApp()
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("回顾").performClick()
        composeTestRule.waitForIdle()

        // 切到回顾页后 SegmentedButton 中的「日历」标签应可见
        composeTestRule.onNodeWithText("日历").assertIsDisplayed()
    }
}
