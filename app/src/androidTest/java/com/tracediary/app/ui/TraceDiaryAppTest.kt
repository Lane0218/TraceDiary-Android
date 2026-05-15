package com.tracediary.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tracediary.app.ui.theme.TraceDiaryTheme
import org.junit.Rule
import org.junit.Test

class TraceDiaryAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavigation_switchesBetweenPrimaryPages() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                TraceDiaryApp()
            }
        }

        composeTestRule.onNodeWithText("往年今日").assertIsDisplayed()

        composeTestRule.onNodeWithText("回顾").performClick()
        composeTestRule.onNodeWithText("查看当天记录").assertIsDisplayed()

        composeTestRule.onNodeWithText("统计").performClick()
        composeTestRule.onNodeWithText("年度热力图").assertIsDisplayed()

        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.onNodeWithText("导出备份").assertIsDisplayed()
    }

    @Test
    fun reviewScreen_switchesBetweenSecondarySections() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                TraceDiaryApp()
            }
        }

        composeTestRule.onNodeWithText("回顾").performClick()
        composeTestRule.onNodeWithText("年度总结").performClick()
        composeTestRule.onNodeWithText("历年总结").assertIsDisplayed()

        composeTestRule.onNodeWithText("搜索").performClick()
        composeTestRule.onNodeWithText("搜索记录内容").assertIsDisplayed()
    }
}
