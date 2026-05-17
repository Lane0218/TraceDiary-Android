package com.tracediary.app.domain.model

import java.time.Instant
import java.time.LocalDate

data class DiaryEntry(
    val date: LocalDate,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState,
)

val DiaryEntry.isRecorded: Boolean
    get() = content.isNotBlank()

fun DiaryEntry.wordCount(): Int = content.plainTextWordCount()

fun String.plainTextWordCount(): Int = length
