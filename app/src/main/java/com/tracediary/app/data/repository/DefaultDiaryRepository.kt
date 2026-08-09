package com.tracediary.app.data.repository

import com.tracediary.app.data.local.DiaryEntryDao
import com.tracediary.app.data.local.DiaryEntryEntity
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class DefaultDiaryRepository(
    private val diaryEntryDao: DiaryEntryDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : DiaryRepository {
    override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> {
        return diaryEntryDao.observeByDate(date.toEpochDay()).map { it?.toDomain() }
    }

    override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> {
        return diaryEntryDao
            .observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
            .map { entries -> entries.map(DiaryEntryEntity::toDomain) }
    }

    override fun observeRecordedDates(): Flow<List<LocalDate>> {
        return diaryEntryDao.observeRecordedEpochDays().map { days ->
            days.map(LocalDate::ofEpochDay)
        }
    }

    override fun observeAllEntries(): Flow<List<DiaryEntry>> {
        return diaryEntryDao.observeAll().map { entries ->
            entries.map(DiaryEntryEntity::toDomain)
        }
    }

    override suspend fun getEntry(date: LocalDate): DiaryEntry? {
        return diaryEntryDao.getByDate(date.toEpochDay())?.toDomain()
    }

    override suspend fun getEntriesBetween(start: LocalDate, endInclusive: LocalDate): List<DiaryEntry> {
        require(!start.isAfter(endInclusive)) { "start must be on or before endInclusive" }
        return diaryEntryDao
            .getBetween(start.toEpochDay(), endInclusive.toEpochDay())
            .map(DiaryEntryEntity::toDomain)
    }

    override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
        val existing = diaryEntryDao.getByDate(date.toEpochDay())?.toDomain()
        val now = Instant.now(clock)
        val entry = DiaryEntry(
            date = date,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = if (existing?.content == content) existing.updatedAt else now,
            syncState = if (existing?.content == content) existing.syncState else SyncState.Pending
        )
        diaryEntryDao.upsert(DiaryEntryEntity.fromDomain(entry))
        return entry
    }

    override suspend fun upsertImportedEntry(entry: DiaryEntry): DiaryEntry {
        diaryEntryDao.upsert(DiaryEntryEntity.fromDomain(entry))
        return entry
    }

    override suspend fun deleteEntry(date: LocalDate) {
        diaryEntryDao.deleteByDate(date.toEpochDay())
    }

    override suspend fun setSyncState(date: LocalDate, state: SyncState) {
        diaryEntryDao.updateSyncState(date.toEpochDay(), state)
    }

}
