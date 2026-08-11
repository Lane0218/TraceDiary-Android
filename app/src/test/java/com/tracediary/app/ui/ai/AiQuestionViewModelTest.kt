package com.tracediary.app.ui.ai

import com.tracediary.app.domain.ai.AiSearchService
import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiAgentProgress
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiFeatureState
import com.tracediary.app.domain.model.AiSourceRef
import com.tracediary.app.domain.model.AiSourceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiQuestionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startNewSession_clearsUiAndAgentContext() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("什么时候洗牙？")

        viewModel.send()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.turns.size)
        assertFalse(viewModel.state.value.isSending)
        viewModel.startNewSession()
        assertEquals(0, viewModel.state.value.turns.size)
        assertEquals("", viewModel.state.value.question)
        assertEquals(1, service.clearCalls)
    }

    @Test
    fun send_movesQuestionToLoadingTurnBeforeAnswerCompletes() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("什么时候洗牙？")

        viewModel.send()
        runCurrent()

        assertEquals("", viewModel.state.value.question)
        assertEquals(1, viewModel.state.value.turns.size)
        assertEquals(AiTurnState.Loading, viewModel.state.value.turns.single().state)
        assertEquals(AiAgentProgress.Understanding, viewModel.state.value.turns.single().progress)
        assertTrue(viewModel.state.value.isSending)

        service.pendingAnswer?.complete(Result.success(service.answer))
        advanceUntilIdle()

        assertEquals(AiTurnState.Answered, viewModel.state.value.turns.single().state)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun cancel_marksLoadingTurnCancelled() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("取消这个问题")

        viewModel.send()
        runCurrent()
        viewModel.cancel()

        assertEquals(AiTurnState.Cancelled, viewModel.state.value.turns.single().state)
        assertEquals("本次搜索已取消", viewModel.state.value.turns.single().statusMessage)
        assertFalse(viewModel.state.value.isSending)
        assertEquals(1, service.cancelCalls)
    }

    @Test
    fun streamingDraftIsClearedOnlyAfterValidatedAnswerArrives() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        service.emitDraft = true
        service.pendingAnswer = CompletableDeferred()
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("流式问题")

        viewModel.send()
        runCurrent()

        assertEquals(AiTurnState.Validating, viewModel.state.value.turns.single().state)
        assertEquals("根据你的记录", viewModel.state.value.turns.single().draftText)
        assertEquals(AiAgentProgress.Validating, viewModel.state.value.turns.single().progress)
        assertTrue(viewModel.state.value.isSending)

        service.pendingAnswer?.complete(Result.success(service.answer))
        advanceUntilIdle()

        assertEquals(AiTurnState.Answered, viewModel.state.value.turns.single().state)
        assertEquals(null, viewModel.state.value.turns.single().draftText)
    }

    @Test
    fun streamingFailureRetractsDraftAndDoesNotExposeAnswer() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        service.emitDraft = true
        service.streamingResult = Result.failure(IllegalStateException("本次回答未通过记录核对，请换个问法"))
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("校验失败的问题")

        viewModel.send()
        advanceUntilIdle()

        val turn = viewModel.state.value.turns.single()
        assertEquals(AiTurnState.Failed, turn.state)
        assertEquals(null, turn.draftText)
        assertEquals(null, turn.answer)
    }
}

private class FakeAiSearchService : AiSearchService {
    override val state = MutableStateFlow(AiFeatureState(availability = AiAvailability.Ready))
    var clearCalls = 0
    var cancelCalls = 0
    var pendingAnswer: CompletableDeferred<Result<AiAnswer>>? = null
    var emitDraft = false
    var streamingResult: Result<AiAnswer>? = null
    val answer = AiAnswer(
        text = "根据你的记录，3 月 18 日洗过牙。",
        evidence = listOf(AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-18"), "今天去洗牙")),
    )

    override suspend fun ask(question: String): Result<AiAnswer> = pendingAnswer?.await() ?: Result.success(answer)

    override suspend fun askStreaming(
        question: String,
        onProgressChanged: suspend (AiAgentProgress) -> Unit,
        onDraftChanged: suspend (String) -> Unit,
    ): Result<AiAnswer> {
        if (!emitDraft) return ask(question)
        onProgressChanged(AiAgentProgress.Generating)
        onDraftChanged("根据你的记录")
        onProgressChanged(AiAgentProgress.Validating)
        return streamingResult ?: ask(question)
    }

    override fun cancelCurrentWork() {
        cancelCalls++
    }
    override fun clearSession() { clearCalls++ }
    override suspend fun confirmAndBuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun rebuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteIndex(): Result<Unit> = Result.success(Unit)
}
