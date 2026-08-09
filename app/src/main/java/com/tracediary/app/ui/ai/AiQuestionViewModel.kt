package com.tracediary.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.ai.AiSearchService
import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiFeatureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiConversationTurn(
    val question: String,
    val answer: AiAnswer,
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
        askJob = viewModelScope.launch {
            _state.update { it.copy(isSending = true, errorMessage = null) }
            try {
                service.ask(question).fold(
                    onSuccess = { answer ->
                        _state.update {
                            it.copy(
                                question = "",
                                turns = it.turns + AiConversationTurn(question, answer),
                                isSending = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(isSending = false, errorMessage = error.safeMessage())
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
        _state.update { it.copy(isSending = false) }
    }

    fun clear() {
        cancel()
        service.clearSession()
        _state.update { it.copy(question = "", turns = emptyList(), errorMessage = null) }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun Throwable.safeMessage(): String = message?.takeIf { it.isNotBlank() } ?: "AI 问答失败，请稍后重试"

    companion object {
        private const val MAX_QUESTION_CHARS = 1_000

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiQuestionViewModel(application.aiSearchCoordinator) }
        }
    }
}
