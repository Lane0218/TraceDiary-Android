package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.ZipInputStream

/** 解析备份 JSON / ZIP 时使用，确保对 ViewModel 暴露统一的中文错误。 */
class BackupParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** 备份合并策略。 */
enum class MergeStrategy {
    /** 仅在备份中的 updatedAt 比本地新时才覆盖；时间相同或更早保留本地。 */
    KeepNewer,

    /** 备份中所有条目无条件覆盖本地。 */
    OverwriteAll,

    /** 已存在的日期/年度跳过，仅写入本地缺失的条目。 */
    SkipExisting,
}

data class ImportOptions(val mergeStrategy: MergeStrategy = MergeStrategy.KeepNewer)

data class ImportResult(
    val importedEntries: Int,
    val skippedEntries: Int,
    val importedSummaries: Int,
    val skippedSummaries: Int,
)

/**
 * 解析备份内容并按合并策略写回 Repository。
 *
 * - 自动识别字节流：以 `PK` 起始的视为 ZIP（TXT/Markdown），否则按 UTF-8 解码尝试 JSON。
 * - JSON 路径保留 [DiaryEntryBackup] / [AnnualSummaryBackup] 中的时间戳；
 * - ZIP 路径按文件名推导日期/年份，时间戳取该日期 23:59 / 该年 12 月 31 日 23:59，
 *   均使用系统默认时区转 Instant。
 * - 写入统一使用 `upsertImportedEntry` / `upsertImportedSummary`，避免被 saveEntry 的 clock 改写。
 */
class ImportDiaryDataUseCase(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend operator fun invoke(
        bytes: ByteArray,
        options: ImportOptions = ImportOptions(),
    ): ImportResult {
        if (bytes.isEmpty()) throw BackupParseException("文件内容为空")
        return if (isZip(bytes)) importZip(bytes, options) else importJson(bytes, options)
    }

    /** 向后兼容：旧接口仍允许只传入 JSON 文本。 */
    suspend operator fun invoke(
        json: String,
        options: ImportOptions = ImportOptions(),
    ): ImportResult = invoke(json.toByteArray(Charsets.UTF_8), options)

    private suspend fun importJson(bytes: ByteArray, options: ImportOptions): ImportResult {
        val backup = parseBackup(bytes.toString(Charsets.UTF_8))
        var importedEntries = 0
        var skippedEntries = 0
        for (entryBackup in backup.entries) {
            val date = parseDate(entryBackup.date) ?: run {
                skippedEntries++
                continue
            }
            val createdAt = parseInstant(entryBackup.createdAt) ?: defaultDiaryInstant(date)
            val updatedAt = parseInstant(entryBackup.updatedAt) ?: createdAt
            val existing = diaryRepository.getEntry(date)
            if (shouldWriteEntry(existing, updatedAt, options.mergeStrategy)) {
                diaryRepository.upsertImportedEntry(
                    DiaryEntry(
                        date = date,
                        content = entryBackup.content,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncState = SyncState.Pending,
                    )
                )
                importedEntries++
            } else {
                skippedEntries++
            }
        }
        var importedSummaries = 0
        var skippedSummaries = 0
        for (summaryBackup in backup.annualSummaries) {
            val createdAt = parseInstant(summaryBackup.createdAt)
                ?: defaultAnnualInstant(summaryBackup.year)
            val updatedAt = parseInstant(summaryBackup.updatedAt) ?: createdAt
            val existing = annualSummaryRepository.getSummary(summaryBackup.year)
            if (shouldWriteSummary(existing, updatedAt, options.mergeStrategy)) {
                annualSummaryRepository.upsertImportedSummary(
                    AnnualSummary(
                        year = summaryBackup.year,
                        content = summaryBackup.content,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncState = SyncState.Pending,
                    )
                )
                importedSummaries++
            } else {
                skippedSummaries++
            }
        }
        return ImportResult(importedEntries, skippedEntries, importedSummaries, skippedSummaries)
    }

    private suspend fun importZip(bytes: ByteArray, options: ImportOptions): ImportResult {
        var importedEntries = 0
        var skippedEntries = 0
        var importedSummaries = 0
        var skippedSummaries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.replace('\\', '/')
                if (name.endsWith("manifest.json")) {
                    zip.closeEntry()
                    continue
                }
                val fileName = name.substringAfterLast('/')
                val baseName = fileName.substringBeforeLast('.', fileName)
                val content = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                when {
                    name.startsWith("entries/") -> {
                        val date = parseDate(baseName)
                        if (date == null) {
                            skippedEntries++
                        } else {
                            val instant = defaultDiaryInstant(date)
                            val existing = diaryRepository.getEntry(date)
                            if (shouldWriteEntry(existing, instant, options.mergeStrategy)) {
                                diaryRepository.upsertImportedEntry(
                                    DiaryEntry(
                                        date = date,
                                        content = content,
                                        createdAt = instant,
                                        updatedAt = instant,
                                        syncState = SyncState.Pending,
                                    )
                                )
                                importedEntries++
                            } else {
                                skippedEntries++
                            }
                        }
                    }
                    name.startsWith("annual/") -> {
                        val year = baseName.toIntOrNull()
                        if (year == null) {
                            skippedSummaries++
                        } else {
                            val instant = defaultAnnualInstant(year)
                            val existing = annualSummaryRepository.getSummary(year)
                            if (shouldWriteSummary(existing, instant, options.mergeStrategy)) {
                                annualSummaryRepository.upsertImportedSummary(
                                    AnnualSummary(
                                        year = year,
                                        content = content,
                                        createdAt = instant,
                                        updatedAt = instant,
                                        syncState = SyncState.Pending,
                                    )
                                )
                                importedSummaries++
                            } else {
                                skippedSummaries++
                            }
                        }
                    }
                    else -> {
                        // 兼容 manifest 之外的任意附加文件：直接忽略，不计入跳过。
                    }
                }
            }
        }
        return ImportResult(importedEntries, skippedEntries, importedSummaries, skippedSummaries)
    }

    private fun parseBackup(payload: String): DiaryBackup {
        if (payload.isBlank()) {
            throw BackupParseException("文件不是有效的备份格式")
        }
        return try {
            json.decodeFromString(DiaryBackup.serializer(), payload)
        } catch (e: SerializationException) {
            throw BackupParseException("文件不是有效的备份格式", e)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization 偶尔抛 IllegalArgumentException
            throw BackupParseException("文件不是有效的备份格式", e)
        }
    }

    private fun shouldWriteEntry(
        existing: DiaryEntry?,
        backupUpdatedAt: Instant,
        strategy: MergeStrategy,
    ): Boolean {
        if (existing == null) return true
        return when (strategy) {
            MergeStrategy.OverwriteAll -> true
            MergeStrategy.SkipExisting -> false
            MergeStrategy.KeepNewer -> backupUpdatedAt.isAfter(existing.updatedAt)
        }
    }

    private fun shouldWriteSummary(
        existing: AnnualSummary?,
        backupUpdatedAt: Instant,
        strategy: MergeStrategy,
    ): Boolean {
        if (existing == null) return true
        return when (strategy) {
            MergeStrategy.OverwriteAll -> true
            MergeStrategy.SkipExisting -> false
            MergeStrategy.KeepNewer -> backupUpdatedAt.isAfter(existing.updatedAt)
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (e: Exception) {
        null
    }

    private fun parseInstant(text: String): Instant? = try {
        Instant.parse(text)
    } catch (e: Exception) {
        null
    }

    private fun defaultDiaryInstant(date: LocalDate): Instant =
        date.atTime(LocalTime.of(23, 59)).atZone(zoneId).toInstant()

    private fun defaultAnnualInstant(year: Int): Instant =
        LocalDate.of(year, 12, 31).atTime(LocalTime.of(23, 59)).atZone(zoneId).toInstant()

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}
