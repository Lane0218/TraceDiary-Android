package com.tracediary.app.ui.security

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.data.security.AppLockState
import com.tracediary.app.ui.theme.TraceDiaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun needSetupStateShowsBrandedCardAndTwoFields() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppLockScreen(
                        state = AppLockState.NeedSetup,
                        rememberPassword = true,
                        errorMessage = null,
                        onRememberChanged = {},
                        onSetupPassword = {},
                        onUnlock = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("TraceDiary").assertIsDisplayed()
        composeTestRule.onNodeWithText("设置应用密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("新密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("确认新密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("在本机记住密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("关闭后每次冷启动都需要输入密码").assertDoesNotExist()
        composeTestRule.onNodeWithText("设置并进入").assertIsDisplayed()
        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
    }

    @Test
    fun lockedStateShowsSinglePasswordFieldAndUnlockAction() {
        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppLockScreen(
                        state = AppLockState.Locked,
                        rememberPassword = false,
                        errorMessage = "密码错误",
                        onRememberChanged = {},
                        onSetupPassword = {},
                        onUnlock = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("TraceDiary").assertIsDisplayed()
        composeTestRule.onNodeWithText("输入应用密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("密码").assertIsDisplayed()
        composeTestRule.onNodeWithText("密码错误").assertIsDisplayed()
        composeTestRule.onNodeWithText("解锁").assertIsDisplayed()
        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }
}
