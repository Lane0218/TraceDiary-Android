package com.tracediary.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tracediary.app.ui.theme.TraceDiaryTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TraceDiaryAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavigation_switchesBetweenPrimaryPages() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                TraceDiaryMainContent(
                    todayContent = { _, _ -> Text("今天页面内容") },
                    reviewContent = { Text("回顾页面内容") },
                    statsContent = { Text("统计页面内容") },
                    settingsContent = { Text("设置页面内容") },
                )
            }
        }

        composeTestRule.onNodeWithText("今天页面内容").assertIsDisplayed()

        composeTestRule.onNodeWithText("回顾").performClick()
        composeTestRule.onNodeWithText("回顾页面内容").assertIsDisplayed()

        composeTestRule.onNodeWithText("统计").performClick()
        composeTestRule.onNodeWithText("统计页面内容").assertIsDisplayed()

        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.onNodeWithText("设置页面内容").assertIsDisplayed()
    }

    @Test
    fun reviewJump_routesDateBackToToday() {
        val targetDate = LocalDate.of(2025, 5, 17)
        composeTestRule.setContent {
            TraceDiaryTheme {
                TraceDiaryMainContent(
                    todayContent = { pendingDate, _ ->
                        Text("待跳转日期：$pendingDate")
                    },
                    reviewContent = { onJumpToToday ->
                        Button(onClick = { onJumpToToday(targetDate) }) {
                            Text("查看当天记录")
                        }
                    },
                    statsContent = {},
                    settingsContent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("回顾").performClick()
        composeTestRule.onNodeWithText("查看当天记录").performClick()
        composeTestRule.onNodeWithText("待跳转日期：2025-05-17").assertIsDisplayed()
    }
}
