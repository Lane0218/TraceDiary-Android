package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 将本地日记与年度总结导出为可读文件。
 *
 * 支持格式：
 * - [ExportFormat.Json]：单个 JSON 文件，结构见 [DiaryBackup]。
 * - [ExportFormat.TextZip] / [ExportFormat.MarkdownZip]：ZIP 压缩包，
 *   按 `entries/yyyy-MM-dd.{txt|md}`、`annual/yyyy.{txt|md}` 落盘，正文为纯文本。
 *
 * 日期使用 ISO-8601 / yyyy-MM-dd，便于人工阅读和跨平台还原。
 */
class ExportDiaryDataUseCase(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(format: ExportFormat = ExportFormat.Json): ExportPayload {
        val entries = diaryRepository.observeAllEntries().first()
        val summaries = annualSummaryRepository.observeSummaries().first()
        val today = LocalDate.now(clock).format(FILE_NAME_DATE)
        return when (format) {
            ExportFormat.Json -> exportJsonZip(entries, summaries, today)
            ExportFormat.TextZip -> exportZip(entries, summaries, "txt", today)
            ExportFormat.MarkdownZip -> exportZip(entries, summaries, "md", today)
        }
    }

    private fun exportJsonZip(
        entries: List<DiaryEntry>,
        summaries: List<AnnualSummary>,
        today: String,
    ): ExportPayload {
        val backup = DiaryBackup(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = Instant.now(clock).toString(),
            entries = entries.map(::toBackup),
            annualSummaries = summaries.map(::toBackup),
        )
        val json = JSON.encodeToString(backup)
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("tracediary_backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(buildManifest("json").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ExportPayload(
            bytes = buffer.toByteArray(),
            mimeType = "application/zip",
            suggestedFileName = "tracediary_backup_${today}_json.zip",
            entryCount = entries.size,
            summaryCount = summaries.size,
        )
    }

    private fun exportZip(
        entries: List<DiaryEntry>,
        summaries: List<AnnualSummary>,
        ext: String,
        today: String,
    ): ExportPayload {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("entries/"))
            zip.closeEntry()
            for (entry in entries) {
                zip.putNextEntry(ZipEntry("entries/${entry.date.format(FILE_NAME_DATE)}.$ext"))
                zip.write(entry.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            if (summaries.isNotEmpty()) {
                zip.putNextEntry(ZipEntry("annual/"))
                zip.closeEntry()
                for (summary in summaries) {
                    zip.putNextEntry(ZipEntry("annual/${summary.year}.$ext"))
                    zip.write(summary.content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(buildManifest(ext).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val name = "tracediary_backup_${today}_${ext}.zip"
        return ExportPayload(
            bytes = buffer.toByteArray(),
            mimeType = "application/zip",
            suggestedFileName = name,
            entryCount = entries.size,
            summaryCount = summaries.size,
        )
    }

    private fun buildManifest(payloadKind: String): String = JSON.encodeToString(
        BackupManifest(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = Instant.now(clock).toString(),
            payloadKind = payloadKind,
        )
    )

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

        private val FILE_NAME_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // prettyPrint 让备份文件可被人工查阅；ignoreUnknownKeys 在导入时由 ImportUseCase 复用。
        internal val JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
