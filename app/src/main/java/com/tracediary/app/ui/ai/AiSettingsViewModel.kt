package com.tracediary.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.ai.AiAgentConnectionResult
import com.tracediary.app.domain.ai.AiAdministrationService
import com.tracediary.app.domain.model.AiFeatureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AiSettingsOperation {
    data object Idle : AiSettingsOperation
    data class Working(val message: String) : AiSettingsOperation
    data class Success(val message: String) : AiSettingsOperation
    data class Failed(val message: String) : AiSettingsOperation
}

data class AiSettingsUiState(
    val feature: AiFeatureState = AiFeatureState(),
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val operation: AiSettingsOperation = AiSettingsOperation.Idle,
)

class AiSettingsViewModel(
    private val service: AiAdministrationService,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AiSettingsUiState(
            feature = service.state.value,
            baseUrl = service.state.value.configuration.baseUrl,
            model = service.state.value.configuration.model,
            apiKey = service.loadApiKeyForEditing(),
        ),
    )
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()
    private var operationJob: Job? = null

    init {
        viewModelScope.launch {
            service.state.collectLatest { feature ->
                _state.update { current ->
                    current.copy(
                        feature = feature,
                        baseUrl = if (current.baseUrl.isBlank()) feature.configuration.baseUrl else current.baseUrl,
                        model = if (current.model.isBlank()) feature.configuration.model else current.model,
                    )
                }
            }
        }
    }

    fun onBaseUrlChanged(value: String) = _state.update { it.copy(baseUrl = value, operation = AiSettingsOperation.Idle) }
    fun onModelChanged(value: String) = _state.update { it.copy(model = value, operation = AiSettingsOperation.Idle) }
    fun onApiKeyChanged(value: String) = _state.update { it.copy(apiKey = value, operation = AiSettingsOperation.Idle) }

    fun saveAndTest() = launchOperation("正在验证模型工具调用能力……") {
        when (val result = service.saveAndTestConnection(state.value.baseUrl, state.value.model, state.value.apiKey)) {
            AiAgentConnectionResult.Success -> "连接与 Tool Calls 验证成功"
            is AiAgentConnectionResult.Failed -> throw IllegalStateException(result.message)
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { service.setEnabled(enabled) }
    }

    fun setBroadAnalysisAllowed(allowed: Boolean) {
        viewModelScope.launch { service.setBroadAnalysisAllowed(allowed) }
    }

    fun downloadModel() = launchOperation("正在下载并校验端侧模型……") {
        service.downloadModel().getOrThrow()
        "端侧模型已就绪"
    }

    fun confirmAndBuildIndex() = launchOperation("正在建立完整加密索引……") {
        service.confirmAndBuildIndex().getOrThrow()
        "本地 AI 索引已就绪"
    }

    fun rebuildIndex() = launchOperation("正在重建加密索引……") {
        service.rebuildIndex().getOrThrow()
        "本地 AI 索引已重建"
    }

    fun deleteIndex() = launchOperation("正在删除本地 AI 索引……") {
        service.deleteIndex().getOrThrow()
        "本地 AI 索引已删除"
    }

    fun cancel() {
        operationJob?.cancel()
        service.cancelCurrentWork()
        _state.update { it.copy(operation = AiSettingsOperation.Idle) }
    }

    fun dismissOperation() = _state.update { it.copy(operation = AiSettingsOperation.Idle) }

    private fun launchOperation(message: String, action: suspend () -> String) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            _state.update { it.copy(operation = AiSettingsOperation.Working(message)) }
            try {
                val success = action()
                _state.update { it.copy(operation = AiSettingsOperation.Success(success)) }
            } catch (_: CancellationException) {
                _state.update { it.copy(operation = AiSettingsOperation.Idle) }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(operation = AiSettingsOperation.Failed(error.message ?: "操作失败"))
                }
            }
        }
    }

    companion object {
        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { AiSettingsViewModel(application.aiSearchCoordinator) }
        }
    }
}
