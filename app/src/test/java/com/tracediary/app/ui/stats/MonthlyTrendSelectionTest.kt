package com.tracediary.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyTrendSelectionTest {
    @Test
    fun wordCountUsesOneDecimalKForThousands() {
        assertEquals("1.0k", formatMonthlyTrendWordCount(1000))
        assertEquals("1.2k", formatMonthlyTrendWordCount(1234))
        assertEquals("2.0k", formatMonthlyTrendWordCount(1999))
    }

    @Test
    fun wordCountKeepsSmallValuesReadable() {
        assertEquals("0", formatMonthlyTrendWordCount(0))
        assertEquals("999", formatMonthlyTrendWordCount(999))
    }
}
