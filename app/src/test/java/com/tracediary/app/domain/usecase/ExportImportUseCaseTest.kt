package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ExportImportUseCaseTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `导出后清空再导入 内容与年度总结全部还原`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(
                entry("2026-05-17", "今天的日记", updated = "2026-05-17T08:00:00Z"),
                entry("2026-05-16", "昨天的日记", updated = "2026-05-16T08:00:00Z"),
            ),
        )
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "2025 总结", updated = "2025-12-31T10:00:00Z")),
        )

        val export = ExportDiaryDataUseCase(diaryRepo, annualRepo, fixedClock)
        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)

        val payload = export()
        diaryRepo.clear()
        annualRepo.clear()

        val result = import(payload.bytes, ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(2, result.importedEntries)
        assertEquals(0, result.skippedEntries)
        assertEquals(1, result.importedSummaries)
        assertEquals(0, result.skippedSummaries)

        assertEquals("今天的日记", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
        assertEquals("昨天的日记", diaryRepo.peek(LocalDate.parse("2026-05-16"))?.content)
        assertEquals("2025 总结", annualRepo.peek(2025)?.content)
    }

    @Test
    fun `KeepNewer 策略下本地较新则跳过 备份较新则覆盖`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(
                // 本地比备份新，应当被保留
                entry("2026-05-17", "本地最新", updated = "2026-05-17T12:00:00Z"),
                // 本地比备份旧，应当被覆盖
                entry("2026-05-16", "本地旧版", updated = "2026-05-16T08:00:00Z"),
            ),
        )
        val annualRepo = FakeAnnualSummaryRepository()

        val backupJson = """
            {
              "version": 1,
              "exportedAt": "2026-05-17T00:00:00Z",
              "entries": [
                {"date":"2026-05-17","content":"备份较旧","createdAt":"2026-05-17T08:00:00Z","updatedAt":"2026-05-17T08:00:00Z"},
                {"date":"2026-05-16","content":"备份较新","createdAt":"2026-05-16T08:00:00Z","updatedAt":"2026-05-16T20:00:00Z"}
              ],
              "annualSummaries": []
            }
        """.trimIndent()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(backupJson, ImportOptions(MergeStrategy.KeepNewer))

        assertEquals(1, result.importedEntries)
        assertEquals(1, result.skippedEntries)
        assertEquals("本地最新", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
        assertEquals("备份较新", diaryRepo.peek(LocalDate.parse("2026-05-16"))?.content)
    }

    @Test
    fun `OverwriteAll 策略下无论新旧都覆盖`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(
                entry("2026-05-17", "本地最新", updated = "2026-05-17T23:00:00Z"),
            ),
        )
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "本地总结", updated = "2025-12-31T23:00:00Z")),
        )

        val backupJson = """
            {
              "version": 1,
              "exportedAt": "2026-05-17T00:00:00Z",
              "entries": [
                {"date":"2026-05-17","content":"备份覆盖","createdAt":"2026-05-17T08:00:00Z","updatedAt":"2026-05-17T08:00:00Z"}
              ],
              "annualSummaries": [
                {"year":2025,"content":"备份总结","createdAt":"2025-12-31T08:00:00Z","updatedAt":"2025-12-31T08:00:00Z"}
              ]
            }
        """.trimIndent()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(backupJson, ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(1, result.importedEntries)
        assertEquals(0, result.skippedEntries)
        assertEquals(1, result.importedSummaries)
        assertEquals("备份覆盖", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
        assertEquals("备份总结", annualRepo.peek(2025)?.content)
    }

    @Test
    fun `SkipExisting 策略下已有日期或年度跳过`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(
                entry("2026-05-17", "已有", updated = "2026-05-17T08:00:00Z"),
            ),
        )
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "已有总结", updated = "2025-12-31T08:00:00Z")),
        )

        val backupJson = """
            {
              "version": 1,
              "exportedAt": "2026-05-17T00:00:00Z",
              "entries": [
                {"date":"2026-05-17","content":"应被跳过","createdAt":"2026-05-17T08:00:00Z","updatedAt":"2026-05-17T20:00:00Z"},
                {"date":"2026-05-15","content":"新条目","createdAt":"2026-05-15T08:00:00Z","updatedAt":"2026-05-15T08:00:00Z"}
              ],
              "annualSummaries": [
                {"year":2025,"content":"应被跳过","createdAt":"2025-12-31T08:00:00Z","updatedAt":"2025-12-31T20:00:00Z"},
                {"year":2024,"content":"新总结","createdAt":"2024-12-31T08:00:00Z","updatedAt":"2024-12-31T08:00:00Z"}
              ]
            }
        """.trimIndent()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(backupJson, ImportOptions(MergeStrategy.SkipExisting))

        assertEquals(1, result.importedEntries)
        assertEquals(1, result.skippedEntries)
        assertEquals(1, result.importedSummaries)
        assertEquals(1, result.skippedSummaries)

        assertEquals("已有", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
        assertEquals("新条目", diaryRepo.peek(LocalDate.parse("2026-05-15"))?.content)
        assertEquals("已有总结", annualRepo.peek(2025)?.content)
        assertEquals("新总结", annualRepo.peek(2024)?.content)
    }

    @Test
    fun `非法 JSON 抛 BackupParseException`() = runTest {
        val import = ImportDiaryDataUseCase(FakeDiaryRepository(), FakeAnnualSummaryRepository())

        val ex = assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking {
                import("这不是 JSON")
            }
        }
        assertEquals("文件不是有效的备份格式", ex.message)
    }

    @Test
    fun `空字符串视为非法备份`() = runTest {
        val import = ImportDiaryDataUseCase(FakeDiaryRepository(), FakeAnnualSummaryRepository())

        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { import("   ") }
        }
    }

    @Test
    fun `无效日期或时间被跳过 但不会让整次导入失败`() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()

        val backupJson = """
            {
              "version": 1,
              "exportedAt": "2026-05-17T00:00:00Z",
              "entries": [
                {"date":"not-a-date","content":"X","createdAt":"2026-05-15T08:00:00Z","updatedAt":"2026-05-15T08:00:00Z"},
                {"date":"2026-05-15","content":"OK","createdAt":"2026-05-15T08:00:00Z","updatedAt":"2026-05-15T08:00:00Z"}
              ],
              "annualSummaries": []
            }
        """.trimIndent()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(backupJson, ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(1, result.importedEntries)
        assertEquals(1, result.skippedEntries)
        assertNotNull(diaryRepo.peek(LocalDate.parse("2026-05-15")))
    }

    @Test
    fun `JSON 导出为 ZIP 包并能被 import 还原`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(
                entry("2026-05-17", "今天", updated = "2026-05-17T08:00:00Z"),
                entry("2026-05-16", "昨天", updated = "2026-05-16T08:00:00Z"),
            ),
        )
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "2025 总结", updated = "2025-12-31T10:00:00Z")),
        )

        val export = ExportDiaryDataUseCase(diaryRepo, annualRepo, fixedClock)
        val payload = export(ExportFormat.Json)

        // 输出必须为 ZIP（前两个字节 PK）。
        assertEquals("application/zip", payload.mimeType)
        assertTrue(payload.suggestedFileName.endsWith(".zip"))
        assertTrue(payload.bytes.size >= 2 && payload.bytes[0] == 0x50.toByte() && payload.bytes[1] == 0x4B.toByte())
        // ZIP 内必含顶层 tracediary_backup.json 与 manifest.json。
        val names = readZipEntryNames(payload.bytes)
        assertTrue(names.contains("tracediary_backup.json"))
        assertTrue(names.contains("manifest.json"))

        diaryRepo.clear()
        annualRepo.clear()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(payload.bytes, ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(2, result.importedEntries)
        assertEquals(1, result.importedSummaries)
        assertEquals("今天", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
        assertEquals("2025 总结", annualRepo.peek(2025)?.content)
    }

    @Test
    fun `TXT ZIP 导出文件名 目录结构 与正文均符合预期`() = runTest {
        val diaryRepo = FakeDiaryRepository(
            initialEntries = listOf(entry("2026-05-17", "Hello", updated = "2026-05-17T08:00:00Z")),
        )
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "Year-2025", updated = "2025-12-31T10:00:00Z")),
        )

        val export = ExportDiaryDataUseCase(diaryRepo, annualRepo, fixedClock)
        val payload = export(ExportFormat.TextZip)

        val items = readZipEntries(payload.bytes)
        assertTrue(items.containsKey("entries/2026-05-17.txt"))
        assertEquals("Hello", items["entries/2026-05-17.txt"])
        assertTrue(items.containsKey("annual/2025.txt"))
        assertEquals("Year-2025", items["annual/2025.txt"])
        assertTrue(items.containsKey("manifest.json"))
    }

    @Test
    fun `Markdown ZIP 导入恢复时间戳为 23 59`() = runTest {
        // 先用 export 构造 Markdown ZIP，避免手写字节流。
        val sourceDiaryRepo = FakeDiaryRepository(
            initialEntries = listOf(entry("2026-05-17", "# 标题", updated = "2026-05-17T08:00:00Z")),
        )
        val sourceAnnualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "## 2025", updated = "2025-12-31T10:00:00Z")),
        )
        val export = ExportDiaryDataUseCase(sourceDiaryRepo, sourceAnnualRepo, fixedClock)
        val payload = export(ExportFormat.MarkdownZip)

        val targetDiaryRepo = FakeDiaryRepository()
        val targetAnnualRepo = FakeAnnualSummaryRepository()
        val import = ImportDiaryDataUseCase(
            targetDiaryRepo,
            targetAnnualRepo,
            zoneId = ZoneOffset.UTC,
        )
        val result = import(payload.bytes, ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(1, result.importedEntries)
        assertEquals(1, result.importedSummaries)
        // 时间戳应为系统默认时区当日 23:59；测试中固定为 UTC。
        assertEquals(
            Instant.parse("2026-05-17T23:59:00Z"),
            targetDiaryRepo.peek(LocalDate.parse("2026-05-17"))?.updatedAt,
        )
    }

    @Test
    fun `非 ZIP 字节按 JSON 路径解析向后兼容`() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val plainJson = """
            {
              "version": 1,
              "exportedAt": "2026-05-17T00:00:00Z",
              "entries": [
                {"date":"2026-05-17","content":"P","createdAt":"2026-05-17T08:00:00Z","updatedAt":"2026-05-17T08:00:00Z"}
              ],
              "annualSummaries": []
            }
        """.trimIndent()

        val import = ImportDiaryDataUseCase(diaryRepo, annualRepo)
        val result = import(plainJson.toByteArray(Charsets.UTF_8), ImportOptions(MergeStrategy.OverwriteAll))

        assertEquals(1, result.importedEntries)
        assertEquals("P", diaryRepo.peek(LocalDate.parse("2026-05-17"))?.content)
    }

    // ---- 测试辅助 ----

    private fun readZipEntryNames(bytes: ByteArray): Set<String> = readZipEntries(bytes).keys

    private fun readZipEntries(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return out
    }

    private fun entry(date: String, content: String, updated: String): DiaryEntry = DiaryEntry(
        date = LocalDate.parse(date),
        content = content,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse(updated),
        syncState = SyncState.Synced,
    )

    private fun summary(year: Int, content: String, updated: String): AnnualSummary = AnnualSummary(
        year = year,
        content = content,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse(updated),
        syncState = SyncState.Synced,
    )

    /** In-memory 假实现，仅覆盖 Export/Import UseCase 用到的接口。 */
    private class FakeDiaryRepository(
        initialEntries: List<DiaryEntry> = emptyList(),
    ) : DiaryRepository {
        private val entries = MutableStateFlow(initialEntries)

        fun peek(date: LocalDate): DiaryEntry? = entries.value.firstOrNull { it.date == date }
        fun clear() {
            entries.value = emptyList()
        }

        override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> =
            entries.map { list -> list.firstOrNull { it.date == date } }

        override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
            entries.map { list -> list.filter { YearMonth.from(it.date) == month } }

        override fun observeRecordedDates(): Flow<List<LocalDate>> =
            entries.map { list -> list.map(DiaryEntry::date) }

        override fun observeAllEntries(): Flow<List<DiaryEntry>> = entries.asStateFlow()

        override suspend fun getEntry(date: LocalDate): DiaryEntry? = peek(date)

        override suspend fun getEntriesBetween(
            start: LocalDate,
            endInclusive: LocalDate,
        ): List<DiaryEntry> = entries.value.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }

        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
            val now = Instant.parse("2026-05-17T00:00:00Z")
            val existing = peek(date)
            val saved = DiaryEntry(
                date = date,
                content = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = if (existing?.content == content) existing.updatedAt else now,
                syncState = if (existing?.content == content) existing.syncState else SyncState.Pending,
            )
            entries.value = entries.value.filterNot { it.date == date } + saved
            return saved
        }

        override suspend fun upsertImportedEntry(entry: DiaryEntry): DiaryEntry {
            entries.value = entries.value.filterNot { it.date == entry.date } + entry
            return entry
        }

        override suspend fun deleteEntry(date: LocalDate) {
            entries.value = entries.value.filterNot { it.date == date }
        }

        override suspend fun searchEntries(
            query: String,
            year: Int?,
            recordedOnly: Boolean,
        ): List<DiaryEntry> = emptyList()

        override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> = emptyList()
    }

    private class FakeAnnualSummaryRepository(
        initialSummaries: List<AnnualSummary> = emptyList(),
    ) : AnnualSummaryRepository {
        private val summaries = MutableStateFlow(initialSummaries)

        fun peek(year: Int): AnnualSummary? = summaries.value.firstOrNull { it.year == year }
        fun clear() {
            summaries.value = emptyList()
        }

        override fun observeSummary(year: Int): Flow<AnnualSummary?> =
            summaries.map { list -> list.firstOrNull { it.year == year } }

        override fun observeSummaries(): Flow<List<AnnualSummary>> = summaries.asStateFlow()

        override suspend fun getSummary(year: Int): AnnualSummary? = peek(year)

        override suspend fun saveSummary(year: Int, content: String): AnnualSummary {
            val now = Instant.parse("2026-05-17T00:00:00Z")
            val existing = peek(year)
            val saved = AnnualSummary(
                year = year,
                content = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = if (existing?.content == content) existing.updatedAt else now,
                syncState = if (existing?.content == content) existing.syncState else SyncState.Pending,
            )
            summaries.value = summaries.value.filterNot { it.year == year } + saved
            return saved
        }

        override suspend fun upsertImportedSummary(summary: AnnualSummary): AnnualSummary {
            summaries.value = summaries.value.filterNot { it.year == summary.year } + summary
            return summary
        }

        override suspend fun deleteSummary(year: Int) {
            summaries.value = summaries.value.filterNot { it.year == year }
        }
    }
}
