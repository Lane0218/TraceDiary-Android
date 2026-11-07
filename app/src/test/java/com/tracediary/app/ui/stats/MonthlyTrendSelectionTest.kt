package com.tracediary.app.ui.stats

import com.tracediary.app.domain.usecase.MonthlyWordCount
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyTrendSelectionTest {
    @Test
    fun selectionShowsExactMonthAndWordCount() {
        assertEquals(
            "2026年11月 · 1,234 字",
            formatMonthlyTrendSelection(MonthlyWordCount(YearMonth.of(2026, 11), 1234)),
        )
    }

    @Test
    fun selectionShowsZeroAndEmptyHint() {
        assertEquals(
            "2025年2月 · 0 字",
            formatMonthlyTrendSelection(MonthlyWordCount(YearMonth.of(2025, 2), 0)),
        )
        assertEquals("点击柱子查看月份字数", formatMonthlyTrendSelection(null))
    }
}
