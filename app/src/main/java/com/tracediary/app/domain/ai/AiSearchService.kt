package com.tracediary.app.domain.ai

import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiFeatureState
import kotlinx.coroutines.flow.StateFlow

interface AiSearchService {
    val state: StateFlow<AiFeatureState>

    suspend fun ask(question: String): Result<AiAnswer>
    fun cancelCurrentWork()
    fun clearSession()
    suspend fun confirmAndBuildIndex(): Result<Unit>
    suspend fun rebuildIndex(): Result<Unit>
    suspend fun deleteIndex(): Result<Unit>
}
