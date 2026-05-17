package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.AnnualSummary
import kotlinx.coroutines.flow.Flow

interface AnnualSummaryRepository {
    fun observeSummary(year: Int): Flow<AnnualSummary?>

    fun observeSummaries(): Flow<List<AnnualSummary>>

    suspend fun getSummary(year: Int): AnnualSummary?

    suspend fun saveSummary(year: Int, content: String): AnnualSummary

    suspend fun deleteSummary(year: Int)
}
