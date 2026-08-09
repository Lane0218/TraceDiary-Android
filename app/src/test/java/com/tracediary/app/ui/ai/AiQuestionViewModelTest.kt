package com.tracediary.app.ui.ai

import com.tracediary.app.domain.ai.AiSearchService
import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiFeatureState
import com.tracediary.app.domain.model.AiSourceRef
import com.tracediary.app.domain.model.AiSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun send_keepsAnswerInMemoryAndClearDropsBothLayers() = runTest(dispatcher) {
        val service = FakeAiSearchService()
        val viewModel = AiQuestionViewModel(service)
        viewModel.onQuestionChanged("什么时候洗牙？")

        viewModel.send()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.turns.size)
        assertFalse(viewModel.state.value.isSending)
        viewModel.clear()
        assertEquals(0, viewModel.state.value.turns.size)
        assertEquals(1, service.clearCalls)
    }
}

private class FakeAiSearchService : AiSearchService {
    override val state = MutableStateFlow(AiFeatureState(availability = AiAvailability.Ready))
    var clearCalls = 0

    override suspend fun ask(question: String): Result<AiAnswer> = Result.success(
        AiAnswer(
            text = "根据你的记录，3 月 18 日洗过牙。",
            evidence = listOf(AiEvidence(AiSourceRef(AiSourceType.Diary, "2026-03-18"), "今天去洗牙")),
        ),
    )

    override fun cancelCurrentWork() = Unit
    override fun clearSession() { clearCalls++ }
    override suspend fun confirmAndBuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun rebuildIndex(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteIndex(): Result<Unit> = Result.success(Unit)
}
