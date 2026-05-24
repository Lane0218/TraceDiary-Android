package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.data.security.CryptoBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.SecretKey

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

internal data class DecodedDiaryPayload(
    val entry: DiaryEntry,
)

internal data class DecodedAnnualPayload(
    val summary: AnnualSummary,
)

fun encodeDiary(entry: DiaryEntry, key: SecretKey): ByteArray {
    val payload = syncJson.encodeToString(entry.toSyncDto()).toByteArray(Charsets.UTF_8)
    val envelope = DiaryEnvelope(
        date = entry.date.format(dateFormatter),
        updatedAt = entry.updatedAt.toString(),
        cipher = CryptoBox.encryptToBase64(key, payload),
    )
    return syncJson.encodeToString(envelope).toByteArray(Charsets.UTF_8)
}

fun decodeDiary(bytes: ByteArray, key: SecretKey): DiaryEntry =
    decodeDiaryPayload(bytes, key).entry

internal fun decodeDiaryPayload(
    bytes: ByteArray,
    key: SecretKey,
): DecodedDiaryPayload {
    val raw = bytes.toString(Charsets.UTF_8)
    val envelope = syncJson.decodeFromString<DiaryEnvelope>(raw)
    val payload = CryptoBox.decryptFromBase64(key, envelope.cipher)
    val dto = syncJson.decodeFromString<SyncDiaryDto>(payload.toString(Charsets.UTF_8))
    return DecodedDiaryPayload(dto.toDomain())
}

fun encodeAnnual(summary: AnnualSummary, key: SecretKey): ByteArray {
    val payload = syncJson.encodeToString(summary.toSyncDto()).toByteArray(Charsets.UTF_8)
    val envelope = AnnualEnvelope(
        year = summary.year,
        updatedAt = summary.updatedAt.toString(),
        cipher = CryptoBox.encryptToBase64(key, payload),
    )
    return syncJson.encodeToString(envelope).toByteArray(Charsets.UTF_8)
}

fun decodeAnnual(bytes: ByteArray, key: SecretKey): AnnualSummary =
    decodeAnnualPayload(bytes, key).summary

internal fun decodeAnnualPayload(
    bytes: ByteArray,
    key: SecretKey,
): DecodedAnnualPayload {
    val raw = bytes.toString(Charsets.UTF_8)
    val envelope = syncJson.decodeFromString<AnnualEnvelope>(raw)
    val payload = CryptoBox.decryptFromBase64(key, envelope.cipher)
    val dto = syncJson.decodeFromString<SyncAnnualDto>(payload.toString(Charsets.UTF_8))
    return DecodedAnnualPayload(dto.toDomain())
}
