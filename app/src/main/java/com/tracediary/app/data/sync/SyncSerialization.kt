package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 单对象同步报文。SyncState 是本地视角的状态，不参与序列化。
 */
@Serializable
data class SyncDiaryDto(
    val date: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val schemaVersion: Int = 1,
)

@Serializable
data class SyncAnnualDto(
    val year: Int,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val schemaVersion: Int = 1,
)

val syncJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun DiaryEntry.toSyncDto(): SyncDiaryDto = SyncDiaryDto(
    date = date.format(dateFormatter),
    content = content,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun AnnualSummary.toSyncDto(): SyncAnnualDto = SyncAnnualDto(
    year = year,
    content = content,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun SyncDiaryDto.toDomain(): DiaryEntry = DiaryEntry(
    date = LocalDate.parse(date, dateFormatter),
    content = content,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    syncState = SyncState.Synced,
)

fun SyncAnnualDto.toDomain(): AnnualSummary = AnnualSummary(
    year = year,
    content = content,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    syncState = SyncState.Synced,
)

fun encodeDiary(entry: DiaryEntry): ByteArray =
    syncJson.encodeToString(entry.toSyncDto()).toByteArray(Charsets.UTF_8)

fun decodeDiary(bytes: ByteArray): DiaryEntry =
    syncJson.decodeFromString<SyncDiaryDto>(bytes.toString(Charsets.UTF_8)).toDomain()

fun encodeAnnual(summary: AnnualSummary): ByteArray =
    syncJson.encodeToString(summary.toSyncDto()).toByteArray(Charsets.UTF_8)

fun decodeAnnual(bytes: ByteArray): AnnualSummary =
    syncJson.decodeFromString<SyncAnnualDto>(bytes.toString(Charsets.UTF_8)).toDomain()
