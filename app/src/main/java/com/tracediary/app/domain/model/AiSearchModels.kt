package com.tracediary.app.domain.model

import java.time.LocalDate

enum class AiSourceType { Diary, AnnualSummary, Statistics, Preferences }

data class AiSourceRef(
    val type: AiSourceType,
    val key: String,
)

data class AiEvidence(
    val source: AiSourceRef,
    val quote: String,
)

data class AiAnswer(
    val text: String,
    val evidence: List<AiEvidence>,
    val insufficientEvidence: Boolean = false,
)

data class AiConfiguration(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val broadAnalysisAllowed: Boolean = false,
    val indexConsentGranted: Boolean = false,
)

sealed interface AiAvailability {
    data object Disabled : AiAvailability
    data object NotConfigured : AiAvailability
    data object ModelMissing : AiAvailability
    data class Downloading(val progress: Float) : AiAvailability
    data object IndexRequired : AiAvailability
    data class Indexing(val completed: Int, val total: Int) : AiAvailability
    data object Ready : AiAvailability
    data class Unavailable(val message: String) : AiAvailability
}

data class AiFeatureState(
    val configuration: AiConfiguration = AiConfiguration(),
    val availability: AiAvailability = AiAvailability.Disabled,
    val modelSizeBytes: Long = 0L,
)

data class AiSearchFilter(
    val sourceTypes: Set<AiSourceType> = setOf(AiSourceType.Diary, AiSourceType.AnnualSummary),
    val startDate: LocalDate? = null,
    val endDateInclusive: LocalDate? = null,
    val limit: Int = 8,
)
