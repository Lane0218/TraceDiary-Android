package com.tracediary.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayBoundaryTest {
    @Test
    fun `business date changes exactly at configured boundary`() {
        val boundary = LocalTime.of(1, 0)

        assertEquals(
            LocalDate.of(2026, 5, 7),
            resolveAppDate(LocalDateTime.of(2026, 5, 8, 0, 20), boundary),
        )
        assertEquals(
            LocalDate.of(2026, 5, 7),
            resolveAppDate(LocalDateTime.of(2026, 5, 8, 0, 59), boundary),
        )
        assertEquals(
            LocalDate.of(2026, 5, 8),
            resolveAppDate(LocalDateTime.of(2026, 5, 8, 1, 0), boundary),
        )
    }

    @Test
    fun `changing boundary changes business date without restarting`() {
        val now = LocalDateTime.of(2026, 5, 8, 1, 0)

        assertEquals(LocalDate.of(2026, 5, 8), resolveAppDate(now, LocalTime.of(1, 0)))
        assertEquals(LocalDate.of(2026, 5, 7), resolveAppDate(now, LocalTime.of(2, 0)))
    }
}
