package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryStatisticsUseCaseTest {
    private val useCase = DiaryStatisticsUseCase()

    @Test
    fun calculate_returnsOverviewCountsAndTrends() {
        val entries = listOf(
            entry("2025-12-31", "旧年"),
            entry("2026-01-01", "新 年"),
            entry("2026-05-15", "今天写了很多"),
            entry("2026-05-16", "继续"),
            entry("2026-05-17", "保持"),
            entry("2026-05-18", "   "),
        )

        val result = useCase.calculate(entries, LocalDate.parse("2026-05-17"))

        assertEquals(5, result.totalEntries)
        assertEquals(15, result.totalWordCount)
        assertEquals(13, result.yearWordCount)
        assertEquals(3, result.monthRecordCount)
        assertEquals(3, result.currentStreakDays)
        assertEquals(4, result.annualRecordCount)
        assertEquals(3, result.monthlyWordTrend.single { it.yearMonth == YearMonth.of(2026, 1) }.wordCount)
        assertEquals(10, result.monthlyWordTrend.single { it.yearMonth == YearMonth.of(2026, 5) }.wordCount)
        assertEquals(12, result.monthlyWordTrend.size)
    }

    @Test
    fun calculateCurrentStreak_allowsYesterdayAsActiveStreak() {
        val entries = listOf(
            entry("2026-05-15", "A"),
            entry("2026-05-16", "B"),
        )

        assertEquals(2, useCase.calculateCurrentStreak(entries, LocalDate.parse("2026-05-17")))
    }

    @Test
    fun calculateCurrentStreak_returnsZeroWhenTodayAndYesterdayAreMissing() {
        val entries = listOf(entry("2026-05-14", "A"))

        assertEquals(0, useCase.calculateCurrentStreak(entries, LocalDate.parse("2026-05-17")))
    }

    @Test
    fun calculateYearlyHeatmap_includesLeapDayAndIntensity() {
        val entries = listOf(
            entry("2024-02-29", "闰年记录"),
            entry("2024-03-01", "长长长长长长长长"),
        )

        val heatmap = useCase.calculateYearlyHeatmap(entries, 2024)
        val leapDay = heatmap.single { it.date == LocalDate.parse("2024-02-29") }
        val emptyDay = heatmap.single { it.date == LocalDate.parse("2024-02-28") }

        assertEquals(366, heatmap.size)
        assertTrue(leapDay.hasRecord)
        assertTrue(leapDay.intensity in 1..4)
        assertFalse(emptyDay.hasRecord)
        assertEquals(0, emptyDay.intensity)
    }

    @Test
    fun calculate_usesLatestEntryWhenDuplicateDatesAppear() {
        val oldEntry = entry("2026-05-17", "旧", updatedAt = Instant.parse("2026-05-17T08:00:00Z"))
        val newEntry = entry("2026-05-17", "新的内容", updatedAt = Instant.parse("2026-05-17T09:00:00Z"))

        val result = useCase.calculate(listOf(oldEntry, newEntry), LocalDate.parse("2026-05-17"))

        assertEquals(1, result.totalEntries)
        assertEquals(4, result.totalWordCount)
    }

    private fun entry(
        date: String,
        content: String,
        updatedAt: Instant = Instant.parse("2026-05-17T00:00:00Z"),
    ): DiaryEntry =
        DiaryEntry(
            date = LocalDate.parse(date),
            content = content,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = updatedAt,
            syncState = SyncState.Synced,
        )
}
