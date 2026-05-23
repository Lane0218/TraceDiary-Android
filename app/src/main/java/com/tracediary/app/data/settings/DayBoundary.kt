package com.tracediary.app.data.settings

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 应用级日界线：在 [boundaryTime] 之前仍视为前一天。
 */
fun resolveAppDate(now: LocalDateTime, boundaryTime: LocalTime): LocalDate {
    val boundaryToday = LocalDateTime.of(now.toLocalDate(), boundaryTime)
    return if (now < boundaryToday) now.toLocalDate().minusDays(1) else now.toLocalDate()
}

fun parseBoundaryTime(text: String): LocalTime {
    val parts = text.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
    return LocalTime.of(hour, 0)
}

fun formatBoundaryTime(time: LocalTime): String =
    "%02d:00".format(time.hour.coerceIn(0, 5))
