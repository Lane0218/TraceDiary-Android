package com.tracediary.app.domain.model

import java.time.Instant

data class AnnualSummary(
    val year: Int,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState,
)
