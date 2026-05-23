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
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return LocalTime.of(hour, minute)
}

fun formatBoundaryTime(time: LocalTime): String =
    "%02d:%02d".format(time.hour.coerceIn(0, 5), time.minute.coerceIn(0, 59))
