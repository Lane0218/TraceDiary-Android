package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.isRecorded
import java.time.LocalDate

internal fun Iterable<DiaryEntry>.latestEntryByDate(): Map<LocalDate, DiaryEntry> =
    groupBy(DiaryEntry::date).mapValues { (_, entries) -> entries.maxBy { it.updatedAt } }

internal fun Iterable<DiaryEntry>.latestRecordedEntries(): List<DiaryEntry> =
    latestEntryByDate().values.filter(DiaryEntry::isRecorded)
