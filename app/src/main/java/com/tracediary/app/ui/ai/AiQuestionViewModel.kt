package com.tracediary.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.ai.AiSearchService
import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiAgentProgress
import com.tracediary.app.domain.model.AiFeatureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiTurnState {
    Loading,
    Streaming,
    Validating,
    Answered,
    Failed,
    Cancelled,
}

data class AiConversationTurn(
    val question: String,
    val answer: AiAnswer? = null,
    val state: AiTurnState,
    val draftText: String? = null,
    val progress: AiAgentProgress? = null,
    val statusMessage: String? = null,
)

data class AiQuestionUiState(
    val feature: AiFeatureState = AiFeatureState(),
    val question: String = "",
    val turns: List<AiConversationTurn> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

class AiQuestionViewModel(
    private val service: AiSearchService,
) : ViewModel() {
    private val _state = MutableStateFlow(AiQuestionUiState(feature = service.state.value))
    val state: StateFlow<AiQuestionUiState> = _state.asStateFlow()
    private var askJob: Job? = null

    init {
        viewModelScope.launch {
            service.state.collectLatest { feature ->
                _state.update { it.copy(feature = feature) }
            }
        }
    }

    fun onQuestionChanged(value: String) {
        _state.update { it.copy(question = value.take(MAX_QUESTION_CHARS), errorMessage = null) }
    }

    fun send() {
        if (_state.value.isSending) return
        val question = _state.value.question.trim()
        if (question.isBlank()) return

        _state.update {
            it.copy(
                question = "",
                turns = it.turns + AiConversationTurn(
                    question = question,
                    state = AiTurnState.Loading,
                    progress = AiAgentProgress.Understanding,
                ),
                isSending = true,
                errorMessage = null,
            )
        }

        askJob = viewModelScope.launch {
            try {
                service.askStreaming(
                    question = question,
                    onProgressChanged = { progress ->
                        updatePendingTurn(question) { turn ->
                            turn.copy(
                                state = when (progress) {
                                    AiAgentProgress.Validating -> AiTurnState.Validating
                                    else -> if (turn.draftText.isNullOrBlank()) {
                                        AiTurnState.Loading
                                    } else {
                                        AiTurnState.Streaming
                                    }
                                },
                                progress = progress,
                            )
                        }
                    },
                    onDraftChanged = { draft ->
                        updatePendingTurn(question) {
                            it.copy(
                                state = if (draft.isBlank()) AiTurnState.Loading else AiTurnState.Streaming,
                                draftText = draft.takeIf { it.isNotBlank() },
                            )
                        }
                    },
                ).fold(
                    onSuccess = { answer ->
                        updatePendingTurn(question) {
                            it.copy(
                                answer = answer,
                                state = AiTurnState.Answered,
                                draftText = null,
                                progress = null,
                                statusMessage = null,
                            )
                        }
                        _state.update { it.copy(isSending = false) }
                    },
                    onFailure = { error ->
                        val message = error.safeMessage()
                        updatePendingTurn(question) {
                            it.copy(
                                state = AiTurnState.Failed,
                                draftText = null,
                                progress = null,
                                statusMessage = message,
                            )
                        }
                        _state.update {
                            it.copy(isSending = false, errorMessage = message)
                        }
                    },
                )
            } catch (_: CancellationException) {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    fun cancel() {
        askJob?.cancel()
        service.cancelCurrentWork()
        _state.update { state ->
            val pendingIndex = state.turns.indexOfLast {
                it.state == AiTurnState.Loading ||
                    it.state == AiTurnState.Streaming ||
                    it.state == AiTurnState.Validating
            }
            if (pendingIndex < 0) {
                state.copy(isSending = false)
            } else {
                state.copy(
                    turns = state.turns.toMutableList().apply {
                        set(
                            pendingIndex,
                            get(pendingIndex).copy(
                                state = AiTurnState.Cancelled,
                                draftText = null,
                                progress = null,
                                statusMessage = "本次搜索已取消",
                            ),
                        )
                    },
                    isSending = false,
                )
            }
        }
    }

    fun startNewSession() {
        cancel()
        service.clearSession()
        _state.update { it.copy(question = "", turns = emptyList(), errorMessage = null) }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun Throwable.safeMessage(): String = message?.takeIf { it.isNotBlank() } ?: "AI 搜索失败，请稍后重试"

    private fun updatePendingTurn(
        question: String,
        transform: (AiConversationTurn) -> AiConversationTurn,
    ) {
        _state.update { state ->
            val pendingIndex = state.turns.indexOfLast {
                it.question == question && (
                    it.state == AiTurnState.Loading ||
                        it.state == AiTurnState.Streaming ||
                        it.state == AiTurnState.Validating
                    )
            }
            if (pendingIndex < 0) {
                state
            } else {
                state.copy(
                    turns = state.turns.toMutableList().apply {
                        set(pendingIndex, transform(get(pendingIndex)))
                    },
                )
            }
        }
    }

    companion object {
        private const val MAX_QUESTION_CHARS = 1_000

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiQuestionViewModel(application.aiSearchCoordinator) }
        }
    }
}
