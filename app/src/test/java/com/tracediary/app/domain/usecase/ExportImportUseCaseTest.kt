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

        val json = export()
        diaryRepo.clear()
        annualRepo.clear()

        val result = import(json, ImportOptions(MergeStrategy.OverwriteAll))

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

    // ---- 测试辅助 ----

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

        override suspend fun deleteSummary(year: Int) {
            summaries.value = summaries.value.filterNot { it.year == year }
        }
    }
}
