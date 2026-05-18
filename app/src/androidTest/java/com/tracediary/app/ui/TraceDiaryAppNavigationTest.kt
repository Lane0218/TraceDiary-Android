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
 * 并且切到「回顾」Tab 后能看到搜索分段按钮文案，对应 SPEC 6.1.4 主导航最小回归。
 *
 * 说明：本类需要应用密码已设置（首次启动会被锁定屏拦截）。在真机调试时
 * 由测试人员先行解锁；CI 仅运行 `gradlew test`，不会执行 androidTest。
 */
@RunWith(AndroidJUnit4::class)
class TraceDiaryAppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavigationLabelsArePresent() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TraceDiaryApp()
                }
            }
        }

        composeTestRule.waitForIdle()

        // 这些文案都来自底部 NavigationBar 的 label，未解锁时仅展示锁屏，需要真机解锁后再断言。
        composeTestRule.onNodeWithText("今天").assertIsDisplayed()
        composeTestRule.onNodeWithText("回顾").assertIsDisplayed()
        composeTestRule.onNodeWithText("统计").assertIsDisplayed()
        composeTestRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun reviewTabShowsSearchSegment() {
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

        // 回顾页 SegmentedButton 现在只剩"搜索 / 年度总结"两个 Tab。
        composeTestRule.onNodeWithText("搜索").assertIsDisplayed()
        composeTestRule.onNodeWithText("年度总结").assertIsDisplayed()
    }
}
