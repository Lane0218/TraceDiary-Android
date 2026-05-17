package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.DiaryEntry
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

    suspend fun deleteEntry(date: LocalDate)

    suspend fun searchEntries(query: String, year: Int? = null, recordedOnly: Boolean = true): List<DiaryEntry>

    suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry>
}
