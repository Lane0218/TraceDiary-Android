package com.tracediary.app.ui.ai

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tracediary.app.data.ai.AiAgentConnectionResult
import com.tracediary.app.domain.ai.AiAdministrationService
import com.tracediary.app.domain.model.AiConfiguration
import com.tracediary.app.domain.model.AiFeatureState
import com.tracediary.app.ui.theme.TraceDiaryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiSettingsPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_usesApprovedOrderAndRemovesBroadAccessControls() {
        val service = FakeAiAdministrationService()
        val viewModel = AiSettingsViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                AiSettingsPage(
                    state = AiSettingsUiState(feature = service.state.value),
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("AI搜索").assertIsDisplayed()
        composeRule.onNodeWithText("端侧模型与加密索引").assertIsDisplayed()
        composeRule.onNodeWithText("下载模型（24.1 MB）").assertIsDisplayed()
        composeRule.onNodeWithText("建立索引").assertIsDisplayed()
        composeRule.onNodeWithText("云端大模型").assertIsDisplayed()
        composeRule.onAllNodesWithText("开启 AI 搜索").assertCountEquals(0)
        composeRule.onAllNodesWithText("允许大范围分析").assertCountEquals(0)
        composeRule.onAllNodesWithText("HTTPS Base URL").assertCountEquals(0)

        val baseUrlTop = composeRule.onNodeWithText("Base URL").fetchSemanticsNode().boundsInRoot.top
        val apiKeyTop = composeRule.onNodeWithText("API Key").fetchSemanticsNode().boundsInRoot.top
        val modelTop = composeRule.onNodeWithText("模型名").fetchSemanticsNode().boundsInRoot.top
        assertTrue(baseUrlTop < apiKeyTop)
        assertTrue(apiKeyTop < modelTop)
    }

    @Test
    fun readyIndex_placesClearToTheRightAndConfirmsBeforeDeleting() {
        val service = FakeAiAdministrationService(
            featureState = AiFeatureState(
                configuration = AiConfiguration(),
                modelSizeBytes = 24_120_382L,
                modelDownloaded = true,
                indexReady = true,
            ),
        )
        val viewModel = AiSettingsViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                AiSettingsPage(
                    state = AiSettingsUiState(feature = service.state.value),
                    viewModel = viewModel,
                )
            }
        }

        val rebuildLeft = composeRule.onNodeWithText("重建索引").fetchSemanticsNode().boundsInRoot.left
        val clearLeft = composeRule.onNodeWithText("清理索引").fetchSemanticsNode().boundsInRoot.left
        assertTrue(rebuildLeft < clearLeft)

        composeRule.onNodeWithText("清理索引").performClick()
        composeRule.onNodeWithText("清理本地索引？").assertIsDisplayed()
        composeRule.onNodeWithText("清理").performClick()
        composeRule.waitForIdle()

        assertEquals(1, service.deleteCalls)
    }
}

private class FakeAiAdministrationService(
    featureState: AiFeatureState = AiFeatureState(
            configuration = AiConfiguration(),
            modelSizeBytes = 24_120_382L,
        ),
) : AiAdministrationService {
    override val state = MutableStateFlow(featureState)
    var deleteCalls = 0

    override fun hasApiKey(): Boolean = true
    override fun loadApiKeyForEditing(): String = "test-key"
    override suspend fun saveAndTestConnection(
        baseUrl: String,
        model: String,
        apiKey: String,
    ): AiAgentConnectionResult = AiAgentConnectionResult.Success

    override suspend fun setEnabled(enabled: Boolean) = Unit
    override suspend fun downloadModel(): Result<Unit> = Result.success(Unit)
    override fun cancelCurrentWork() = Unit
    override suspend fun confirmAndBuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun rebuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteIndex(): Result<Unit> {
        deleteCalls += 1
        return Result.success(Unit)
    }
}
