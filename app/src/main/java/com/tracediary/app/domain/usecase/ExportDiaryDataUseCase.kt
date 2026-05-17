package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 将本地日记与年度总结全部读取出来，序列化为可读 JSON 字符串。
 *
 * 输出格式见 [DiaryBackup]。日期使用 ISO-8601（Instant.toString），
 * 日记日期使用 yyyy-MM-dd，便于人工阅读和跨平台还原。
 */
class ExportDiaryDataUseCase(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(): String {
        val entries = diaryRepository.observeAllEntries().first()
        val summaries = annualSummaryRepository.observeSummaries().first()

        val backup = DiaryBackup(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = Instant.now(clock).toString(),
            entries = entries.map(::toBackup),
            annualSummaries = summaries.map(::toBackup),
        )

        return JSON.encodeToString(backup)
    }

    private fun toBackup(entry: DiaryEntry): DiaryEntryBackup = DiaryEntryBackup(
        date = entry.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
        content = entry.content,
        createdAt = entry.createdAt.toString(),
        updatedAt = entry.updatedAt.toString(),
    )

    private fun toBackup(summary: AnnualSummary): AnnualSummaryBackup = AnnualSummaryBackup(
        year = summary.year,
        content = summary.content,
        createdAt = summary.createdAt.toString(),
        updatedAt = summary.updatedAt.toString(),
    )

    companion object {
        const val CURRENT_BACKUP_VERSION: Int = 1

        // prettyPrint 让备份文件可被人工查阅；ignoreUnknownKeys 在导入时由 ImportUseCase 复用。
        internal val JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
