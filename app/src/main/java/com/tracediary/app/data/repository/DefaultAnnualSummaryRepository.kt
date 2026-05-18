package com.tracediary.app.data.repository

import com.tracediary.app.data.local.AnnualSummaryDao
import com.tracediary.app.data.local.AnnualSummaryEntity
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class DefaultAnnualSummaryRepository(
    private val annualSummaryDao: AnnualSummaryDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : AnnualSummaryRepository {
    override fun observeSummary(year: Int): Flow<AnnualSummary?> {
        return annualSummaryDao.observeByYear(year).map { it?.toDomain() }
    }

    override fun observeSummaries(): Flow<List<AnnualSummary>> {
        return annualSummaryDao.observeAll().map { summaries ->
            summaries.map(AnnualSummaryEntity::toDomain)
        }
    }

    override suspend fun getSummary(year: Int): AnnualSummary? {
        return annualSummaryDao.getByYear(year)?.toDomain()
    }

    override suspend fun saveSummary(year: Int, content: String): AnnualSummary {
        val existing = annualSummaryDao.getByYear(year)?.toDomain()
        val now = Instant.now(clock)
        val summary = AnnualSummary(
            year = year,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = if (existing?.content == content) existing.updatedAt else now,
            syncState = if (existing?.content == content) existing.syncState else SyncState.Pending
        )
        annualSummaryDao.upsert(AnnualSummaryEntity.fromDomain(summary))
        return summary
    }

    override suspend fun deleteSummary(year: Int) {
        annualSummaryDao.deleteByYear(year)
    }

    override suspend fun setSyncState(year: Int, state: SyncState) {
        annualSummaryDao.updateSyncState(year, state)
    }
}
