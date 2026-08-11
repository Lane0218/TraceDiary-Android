package com.tracediary.app.ui.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.domain.ai.AiSearchService
import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiAgentProgress
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiFeatureState
import com.tracediary.app.domain.model.AiSourceRef
import com.tracediary.app.domain.model.AiSourceType
import com.tracediary.app.ui.theme.TraceDiaryTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiQuestionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun evidenceSheetAndNewSessionKeepConversationLightweight() {
        val service = FakeAiSearchService()
        val viewModel = AiQuestionViewModel(service)
        var openedSource: AiSourceRef? = null

        composeRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AiQuestionScreen(
                        onOpenEvidence = { openedSource = it.source },
                        viewModel = viewModel,
                    )
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("什么时候洗牙？")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("参考了 1 条资料")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("参考了 1 条资料").assertIsDisplayed()
        composeRule.onNodeWithText("参考了 1 条资料").performClick()
        composeRule.onNodeWithText("1 条资料").assertIsDisplayed()
        composeRule.onNodeWithText("2026年3月18日").performClick()
        composeRule.runOnIdle {
            assertEquals(AiSourceRef(AiSourceType.Diary, "2026-03-18"), openedSource)
        }

        composeRule.onNodeWithContentDescription("开始新会话").performClick()
        composeRule.onNodeWithText("开始新会话？").assertIsDisplayed()
        composeRule.onNodeWithText("开始").performClick()
        composeRule.onNodeWithText("什么时候洗牙？").assertDoesNotExist()
        composeRule.onNodeWithText("根据你的记录，3 月 18 日洗过牙。").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, service.clearCalls) }
    }

    @Test
    fun unsentDraftCanStartNewSession() {
        val service = FakeAiSearchService()
        val viewModel = AiQuestionViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AiQuestionScreen(
                        onOpenEvidence = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("还没发送的问题")
        composeRule.onNodeWithContentDescription("开始新会话").performClick()
        composeRule.onNodeWithText("开始新会话？").assertIsDisplayed()
        composeRule.onNodeWithText("开始").performClick()
        composeRule.runOnIdle {
            assertEquals("", viewModel.state.value.question)
            assertEquals(1, service.clearCalls)
        }
    }

    @Test
    fun emptyStateMatchesReviewPagesAndSendingMovesQuestionIntoConversation() {
        val service = FakeAiSearchService()
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AiQuestionScreen(
                        onOpenEvidence = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        composeRule.onNodeWithText("问问你的记录").assertIsDisplayed()
        composeRule.onNodeWithText("可以问：去年去了哪些城市？今年吃过哪些不错的餐馆？上次洗牙是什么时候？")
            .assertDoesNotExist()

        composeRule.onNode(hasSetTextAction()).performTextInput("什么时候洗牙？")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("正在理解问题…").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("什么时候洗牙？").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("", viewModel.state.value.question) }

        service.pendingAnswer?.complete(
            Result.success(
                AiAnswer(
                    text = "根据你的记录，3 月 18 日洗过牙。",
                    evidence = emptyList(),
                ),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("根据你的记录，3 月 18 日洗过牙。")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun cancelUsesSendButtonAndLeavesCancelledAnswerBubble() {
        val service = FakeAiSearchService()
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AiQuestionScreen(
                        onOpenEvidence = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("取消这个问题")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("取消").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNodeWithText("本次搜索已取消").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, service.cancelCalls) }
    }

    @Test
    fun streamingDraftReplacesPlaceholderBeforeValidatedAnswer() {
        val service = FakeAiSearchService()
        service.emitDraft = true
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)

        composeRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AiQuestionScreen(onOpenEvidence = {}, viewModel = viewModel)
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("流式问题")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("正在流式生成回答").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("正在流式生成回答").assertIsDisplayed()
        composeRule.onNodeWithText("正在核对引用资料…").assertIsDisplayed()
        composeRule.onNodeWithText("参考了 1 条资料").assertDoesNotExist()

        service.pendingAnswer?.complete(
            Result.success(
                AiAnswer(
                    text = "根据你的记录，3 月 18 日洗过牙。",
                    evidence = listOf(
                        AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-18"), "今天下午去洗牙"),
                    ),
                ),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("参考了 1 条资料").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("正在流式生成回答").assertDoesNotExist()
    }

}

private class FakeAiSearchService : AiSearchService {
    override val state = MutableStateFlow(AiFeatureState(availability = AiAvailability.Ready))
    var clearCalls = 0
    var cancelCalls = 0
    var pendingAnswer: CompletableDeferred<Result<AiAnswer>>? = null
    var emitDraft = false

    override suspend fun ask(question: String): Result<AiAnswer> = pendingAnswer?.await() ?: Result.success(
        AiAnswer(
            text = "根据你的记录，3 月 18 日洗过牙。",
            evidence = listOf(
                AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-18"), "今天下午去洗牙"),
                AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-18"), "医生说恢复不错"),
            ),
        ),
    )

    override suspend fun askStreaming(
        question: String,
        onProgressChanged: suspend (AiAgentProgress) -> Unit,
        onDraftChanged: suspend (String) -> Unit,
    ): Result<AiAnswer> {
        if (!emitDraft) return ask(question)
        onProgressChanged(AiAgentProgress.Generating)
        onDraftChanged("正在流式生成回答")
        onProgressChanged(AiAgentProgress.Validating)
        return ask(question)
    }

    override fun cancelCurrentWork() {
        cancelCalls++
    }
    override fun clearSession() {
        clearCalls++
    }
    override suspend fun confirmAndBuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun rebuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteIndex(): Result<Unit> = Result.success(Unit)
}
