package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class CalendarDay(
    val date: LocalDate,
    val isInCurrentMonth: Boolean,
    val hasRecord: Boolean,
)

class DiaryCalendarUseCase {
    fun weekDates(
        containingDate: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    ): List<LocalDate> {
        val daysAfterFirstDay = Math.floorMod(
            containingDate.dayOfWeek.value - firstDayOfWeek.value,
            DaysPerWeek,
        )
        val start = containingDate.minusDays(daysAfterFirstDay.toLong())
        return (0 until DaysPerWeek).map { offset -> start.plusDays(offset.toLong()) }
    }

    fun monthGrid(
        yearMonth: YearMonth,
        entries: List<DiaryEntry>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    ): List<CalendarDay> {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()
        val gridStart = weekDates(firstOfMonth, firstDayOfWeek).first()
        val gridEnd = weekDates(lastOfMonth, firstDayOfWeek).last()
        val recordedDates = recordedDates(entries)
        val days = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1

        return (0 until days).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            CalendarDay(
                date = date,
                isInCurrentMonth = YearMonth.from(date) == yearMonth,
                hasRecord = date in recordedDates,
            )
        }
    }

    fun recordedDates(
        entries: List<DiaryEntry>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Set<LocalDate> =
        entries.latestRecordedEntries()
            .asSequence()
            .filter { entry -> startDate == null || !entry.date.isBefore(startDate) }
            .filter { entry -> endDate == null || !entry.date.isAfter(endDate) }
            .map(DiaryEntry::date)
            .toSet()

    fun recordedDatesInMonth(entries: List<DiaryEntry>, yearMonth: YearMonth): Set<LocalDate> =
        recordedDates(entries, yearMonth.atDay(1), yearMonth.atEndOfMonth())

    fun previousYearsOnThisDay(entries: List<DiaryEntry>, date: LocalDate): List<DiaryEntry> =
        entries.latestRecordedEntries()
            .asSequence()
            .filter { entry -> entry.date.isBefore(date) }
            .filter { entry -> entry.date.month == date.month && entry.date.dayOfMonth == date.dayOfMonth }
            .sortedByDescending(DiaryEntry::date)
            .toList()

    fun sameDayEntries(entries: List<DiaryEntry>, date: LocalDate): List<DiaryEntry> =
        entries.latestRecordedEntries()
            .asSequence()
            .filter { entry -> entry.date != date }
            .filter { entry -> entry.date.month == date.month && entry.date.dayOfMonth == date.dayOfMonth }
            .sortedByDescending(DiaryEntry::date)
            .toList()

    fun sortSameDayEntries(
        entries: List<DiaryEntry>,
        latestFirst: Boolean,
    ): List<DiaryEntry> =
        if (latestFirst) {
            entries.sortedByDescending(DiaryEntry::date)
        } else {
            entries.sortedBy(DiaryEntry::date)
        }

    private companion object {
        const val DaysPerWeek = 7
    }
}
