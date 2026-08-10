package com.tracediary.app.domain.ai

import com.tracediary.app.data.ai.AiAgentConnectionResult
import com.tracediary.app.domain.model.AiFeatureState
import kotlinx.coroutines.flow.StateFlow

interface AiAdministrationService {
    val state: StateFlow<AiFeatureState>
    fun hasApiKey(): Boolean
    fun loadApiKeyForEditing(): String
    suspend fun saveAndTestConnection(baseUrl: String, model: String, apiKey: String): AiAgentConnectionResult
    suspend fun setEnabled(enabled: Boolean)
    suspend fun downloadModel(): Result<Unit>
    fun cancelCurrentWork()
    suspend fun confirmAndBuildIndex(): Result<Unit>
    suspend fun rebuildIndex(): Result<Unit>
    suspend fun deleteIndex(): Result<Unit>
}
