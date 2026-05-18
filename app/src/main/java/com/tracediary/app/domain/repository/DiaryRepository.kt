package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface DiaryRepository {
    fun observeEntry(date: LocalDate): Flow<DiaryEntry?>

    fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>>

    fun observeRecordedDates(): Flow<List<LocalDate>>

    fun observeAllEntries(): Flow<List<DiaryEntry>>

    suspend fun getEntry(date: LocalDate): DiaryEntry?

    suspend fun getEntriesBetween(start: LocalDate, endInclusive: LocalDate): List<DiaryEntry>

    suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry

    /**
     * 导入专用写入：保留外部传入的 createdAt / updatedAt / syncState，
     * 用于备份恢复场景，不复用 [saveEntry] 的 clock 推进逻辑。
     *
     * 默认实现退化为 [saveEntry]，让 fake/test 实现无需为导入功能补 override；
     * 生产实现 [com.tracediary.app.data.repository.DefaultDiaryRepository] 必须重写此方法。
     */
    suspend fun upsertImportedEntry(entry: DiaryEntry): DiaryEntry = saveEntry(entry.date, entry.content)

    suspend fun deleteEntry(date: LocalDate)

    suspend fun searchEntries(query: String, year: Int? = null, recordedOnly: Boolean = true): List<DiaryEntry>

    suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry>

    /**
     * 更新本地条目的同步状态。条目不存在时静默忽略。
     *
     * 默认空实现：旧的 fake/test 实现无需为同步功能补 override。
     * 生产实现 [com.tracediary.app.data.repository.DefaultDiaryRepository] 必须重写此方法。
     */
    suspend fun setSyncState(date: LocalDate, state: SyncState) = Unit
}
