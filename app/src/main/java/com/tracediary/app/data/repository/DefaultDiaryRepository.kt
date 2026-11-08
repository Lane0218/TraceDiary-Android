package com.tracediary.app.data.repository

import android.content.Context
import android.util.Log
import com.tracediary.app.data.local.DiaryEntryDao
import com.tracediary.app.data.local.DiaryEntryEntity
import com.tracediary.app.data.notifications.ReminderNotification
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.settings.UserPreferencesRepository
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
    private val clock: Clock = Clock.systemDefaultZone(),
    private val reminderContext: Context? = null,
    private val reminderPreferences: UserPreferencesRepository? = null,
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
        dismissReminderIfRecorded(entry)
        if (entry.content.isBlank()) resetReminderIfCleared(entry.date)
        return entry
    }

    override suspend fun upsertImportedEntry(entry: DiaryEntry): DiaryEntry {
        diaryEntryDao.upsert(DiaryEntryEntity.fromDomain(entry))
        dismissReminderIfRecorded(entry)
        if (entry.content.isBlank()) resetReminderIfCleared(entry.date)
        return entry
    }

    override suspend fun deleteEntry(date: LocalDate) {
        diaryEntryDao.deleteByDate(date.toEpochDay())
        resetReminderIfCleared(date)
    }

    override suspend fun setSyncState(date: LocalDate, state: SyncState) {
        diaryEntryDao.updateSyncState(date.toEpochDay(), state)
    }

    private suspend fun resetReminderIfCleared(date: LocalDate) {
        val preferences = reminderPreferences ?: return
        try {
            ReminderNotification.resetIfContentCleared(preferences, date)
        } catch (error: Exception) {
            Log.w(DailyReminderScheduler.TAG, "reminder marker reset failed date=$date error=${error.javaClass.simpleName}")
        }
    }

    private suspend fun dismissReminderIfRecorded(entry: DiaryEntry) {
        if (entry.content.isBlank()) return
        val context = reminderContext ?: return
        val preferences = reminderPreferences ?: return
        try {
            ReminderNotification.dismissIfRecorded(context, preferences, entry.date)
        } catch (error: Exception) {
            // 通知清理失败不能把已经提交到数据库的日记误报为保存失败。
            Log.w(DailyReminderScheduler.TAG, "reminder dismissal failed date=${entry.date} error=${error.javaClass.simpleName}")
        }
    }
}
