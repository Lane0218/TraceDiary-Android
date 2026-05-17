package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.wordCount
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

data class DiaryStatistics(
    val totalEntries: Int,
    val totalWordCount: Int,
    val yearWordCount: Int,
    val monthRecordCount: Int,
    val currentStreakDays: Int,
    val annualRecordCount: Int,
    val monthlyWordTrend: List<MonthlyWordCount>,
    val yearlyHeatmap: List<HeatmapDay>,
)

data class MonthlyWordCount(
    val yearMonth: YearMonth,
    val wordCount: Int,
)

data class HeatmapDay(
    val date: LocalDate,
    val wordCount: Int,
    val hasRecord: Boolean,
    val intensity: Int,
)

class DiaryStatisticsUseCase {
    fun calculate(entries: List<DiaryEntry>, referenceDate: LocalDate): DiaryStatistics {
        val recordedEntries = entries.latestRecordedEntries()

        return DiaryStatistics(
            totalEntries = recordedEntries.size,
            totalWordCount = recordedEntries.sumOf(DiaryEntry::wordCount),
            yearWordCount = calculateYearWordCount(recordedEntries, referenceDate.year),
            monthRecordCount = calculateMonthRecordCount(recordedEntries, YearMonth.from(referenceDate)),
            currentStreakDays = calculateCurrentStreak(recordedEntries, referenceDate),
            annualRecordCount = calculateAnnualRecordCount(recordedEntries, referenceDate.year),
            monthlyWordTrend = calculateMonthlyWordTrend(recordedEntries, referenceDate.year),
            yearlyHeatmap = calculateYearlyHeatmap(recordedEntries, referenceDate.year),
        )
    }

    fun calculateYearWordCount(entries: List<DiaryEntry>, year: Int): Int =
        entries.latestRecordedEntries()
            .filter { entry -> entry.date.year == year }
            .sumOf(DiaryEntry::wordCount)

    fun calculateMonthRecordCount(entries: List<DiaryEntry>, yearMonth: YearMonth): Int =
        entries.latestRecordedEntries()
            .count { entry -> YearMonth.from(entry.date) == yearMonth }

    fun calculateAnnualRecordCount(entries: List<DiaryEntry>, year: Int): Int =
        entries.latestRecordedEntries().count { entry -> entry.date.year == year }

    fun calculateCurrentStreak(entries: List<DiaryEntry>, referenceDate: LocalDate): Int {
        val recordedDates = entries.latestRecordedEntries().map(DiaryEntry::date).toSet()
        var cursor = when {
            referenceDate in recordedDates -> referenceDate
            referenceDate.minusDays(1) in recordedDates -> referenceDate.minusDays(1)
            else -> return 0
        }
        var days = 0
        while (cursor in recordedDates) {
            days += 1
            cursor = cursor.minusDays(1)
        }
        return days
    }

    fun calculateMonthlyWordTrend(entries: List<DiaryEntry>, year: Int): List<MonthlyWordCount> {
        val entriesByMonth = entries.latestRecordedEntries().groupBy { entry -> YearMonth.from(entry.date) }
        return (1..12).map { month ->
            val yearMonth = YearMonth.of(year, month)
            MonthlyWordCount(
                yearMonth = yearMonth,
                wordCount = entriesByMonth[yearMonth].orEmpty().sumOf(DiaryEntry::wordCount),
            )
        }
    }

    fun calculateYearlyHeatmap(entries: List<DiaryEntry>, year: Int): List<HeatmapDay> {
        val entriesByDate = entries.latestRecordedEntries()
            .filter { entry -> entry.date.year == year }
            .associateBy(DiaryEntry::date)
        val maxWordCount = entriesByDate.values.maxOfOrNull(DiaryEntry::wordCount).orZero()
        val start = LocalDate.of(year, 1, 1)
        val daysInYear = if (start.isLeapYear) 366 else 365

        return (0 until daysInYear).map { dayOffset ->
            val date = start.plusDays(dayOffset.toLong())
            val wordCount = entriesByDate[date]?.wordCount().orZero()
            HeatmapDay(
                date = date,
                wordCount = wordCount,
                hasRecord = wordCount > 0,
                intensity = calculateHeatmapIntensity(wordCount, maxWordCount),
            )
        }
    }

    private fun calculateHeatmapIntensity(wordCount: Int, maxWordCount: Int): Int {
        if (wordCount <= 0 || maxWordCount <= 0) return 0
        return ceil(wordCount.toDouble() / maxWordCount.toDouble() * MaxHeatmapIntensity)
            .toInt()
            .coerceIn(1, MaxHeatmapIntensity)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private companion object {
        const val MaxHeatmapIntensity = 4
    }
}
