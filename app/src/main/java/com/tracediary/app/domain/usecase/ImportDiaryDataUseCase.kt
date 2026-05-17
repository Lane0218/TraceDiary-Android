package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** 解析备份 JSON 时使用，确保对 ViewModel 暴露统一的中文错误。 */
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
 * 解析备份 JSON 并按合并策略写回 Repository。
 *
 * Repository.saveEntry / saveSummary 内部使用 clock 决定 updatedAt，
 * 因此这里直接调用即可让本地条目刷新为"今天"，避免在 Domain 层注入 clock。
 *
 * 解析失败统一抛 [BackupParseException]，由 ViewModel 翻译为用户文案。
 */
class ImportDiaryDataUseCase(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
) {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend operator fun invoke(
        json: String,
        options: ImportOptions = ImportOptions(),
    ): ImportResult {
        val backup = parseBackup(json)

        var importedEntries = 0
        var skippedEntries = 0
        for (entryBackup in backup.entries) {
            val date = parseDate(entryBackup.date) ?: run {
                skippedEntries++
                continue
            }
            val backupUpdatedAt = parseInstant(entryBackup.updatedAt) ?: Instant.EPOCH
            val existing = diaryRepository.getEntry(date)
            if (shouldWriteEntry(existing, backupUpdatedAt, options.mergeStrategy)) {
                diaryRepository.saveEntry(date, entryBackup.content)
                importedEntries++
            } else {
                skippedEntries++
            }
        }

        var importedSummaries = 0
        var skippedSummaries = 0
        for (summaryBackup in backup.annualSummaries) {
            val backupUpdatedAt = parseInstant(summaryBackup.updatedAt) ?: Instant.EPOCH
            val existing = annualSummaryRepository.getSummary(summaryBackup.year)
            if (shouldWriteSummary(existing, backupUpdatedAt, options.mergeStrategy)) {
                annualSummaryRepository.saveSummary(summaryBackup.year, summaryBackup.content)
                importedSummaries++
            } else {
                skippedSummaries++
            }
        }

        return ImportResult(
            importedEntries = importedEntries,
            skippedEntries = skippedEntries,
            importedSummaries = importedSummaries,
            skippedSummaries = skippedSummaries,
        )
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
}
