package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryCalendarUseCaseTest {
    private val useCase = DiaryCalendarUseCase()

    @Test
    fun weekDates_usesSundayAsDefaultFirstDay() {
        val week = useCase.weekDates(LocalDate.parse("2026-05-17"))

        assertEquals(LocalDate.parse("2026-05-17"), week.first())
        assertEquals(LocalDate.parse("2026-05-23"), week.last())
        assertEquals(7, week.size)
    }

    @Test
    fun weekDates_supportsMondayFirstDay() {
        val week = useCase.weekDates(LocalDate.parse("2026-05-17"), DayOfWeek.MONDAY)

        assertEquals(LocalDate.parse("2026-05-11"), week.first())
        assertEquals(LocalDate.parse("2026-05-17"), week.last())
    }

    @Test
    fun monthGrid_marksCurrentMonthAndRecordedDates() {
        val grid = useCase.monthGrid(
            yearMonth = YearMonth.of(2026, 5),
            entries = listOf(entry("2026-05-17", "有记录"), entry("2026-05-18", "  ")),
        )

        assertEquals(LocalDate.parse("2026-04-26"), grid.first().date)
        assertEquals(LocalDate.parse("2026-06-06"), grid.last().date)
        assertEquals(42, grid.size)
        assertTrue(grid.single { it.date == LocalDate.parse("2026-05-17") }.hasRecord)
        assertFalse(grid.single { it.date == LocalDate.parse("2026-05-18") }.hasRecord)
        assertFalse(grid.first().isInCurrentMonth)
    }

    @Test
    fun recordedDates_filtersRangeAndIgnoresBlankContent() {
        val entries = listOf(
            entry("2026-05-15", "A"),
            entry("2026-05-16", " "),
            entry("2026-05-17", "B"),
        )

        val dates = useCase.recordedDates(
            entries,
            startDate = LocalDate.parse("2026-05-16"),
            endDate = LocalDate.parse("2026-05-17"),
        )

        assertEquals(setOf(LocalDate.parse("2026-05-17")), dates)
    }

    @Test
    fun previousYearsOnThisDay_returnsPastEntriesNewestFirst() {
        val entries = listOf(
            entry("2024-05-17", "前年"),
            entry("2025-05-17", "去年"),
            entry("2026-05-17", "今年"),
            entry("2023-05-18", "不同日期"),
        )

        val result = useCase.previousYearsOnThisDay(entries, LocalDate.parse("2026-05-17"))

        assertEquals(
            listOf(LocalDate.parse("2025-05-17"), LocalDate.parse("2024-05-17")),
            result.map(DiaryEntry::date),
        )
    }

    @Test
    fun previousYearsOnThisDay_matchesLeapDayOnlyWhenLeapDayExists() {
        val entries = listOf(
            entry("2020-02-29", "闰日"),
            entry("2023-02-28", "非闰日"),
        )

        val result = useCase.previousYearsOnThisDay(entries, LocalDate.parse("2024-02-29"))

        assertEquals(listOf(LocalDate.parse("2020-02-29")), result.map(DiaryEntry::date))
    }

    private fun entry(date: String, content: String): DiaryEntry =
        DiaryEntry(
            date = LocalDate.parse(date),
            content = content,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-17T00:00:00Z"),
            syncState = SyncState.Synced,
        )
}
