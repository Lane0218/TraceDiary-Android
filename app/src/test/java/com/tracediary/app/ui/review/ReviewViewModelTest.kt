package com.tracediary.app.ui.review

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var viewModelJob: Job

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModelJob = SupervisorJob()
    }

    @After
    fun tearDown() {
        viewModelJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `切换月份后 displayMonth 与 monthGrid 同步更新`() = runTest(dispatcher) {
        val repo = FakeDiaryRepository(initialEntries = listOf(entry("2026-05-17", "今天")))
        val viewModel = createViewModel(repo, FakeAnnualSummaryRepository())
        advanceUntilIdle()

        // 初始为 2026-05
        assertEquals(YearMonth.of(2026, 5), viewModel.state.value.calendar.displayMonth)
        assertTrue(viewModel.state.value.calendar.monthGrid.isNotEmpty())

        viewModel.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 4), viewModel.state.value.calendar.displayMonth)

        viewModel.onNextMonth()
        viewModel.onNextMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 6), viewModel.state.value.calendar.displayMonth)

        // monthGrid 长度始终是 7 的倍数
        assertEquals(0, viewModel.state.value.calendar.monthGrid.size % 7)

        viewModelJob.cancel()
    }

    @Test
    fun `选中日期后 selectedEntry 与 previousYearsOnThisDay 正确加载`() = runTest(dispatcher) {
        val entries = listOf(
            entry("2024-05-17", "前年五一七"),
            entry("2025-05-17", "去年五一七"),
            entry("2026-05-17", "今天五一七"),
            entry("2026-05-15", "五一五"),
        )
        val repo = FakeDiaryRepository(initialEntries = entries)
        val viewModel = createViewModel(repo, FakeAnnualSummaryRepository())
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.parse("2026-05-15"))
        advanceUntilIdle()

        val calendar = viewModel.state.value.calendar
        assertEquals(LocalDate.parse("2026-05-15"), calendar.selectedDate)
        assertNotNull(calendar.selectedEntry)
        assertEquals("五一五", calendar.selectedEntry?.content)
        assertTrue(calendar.previousYearsOnThisDay.isEmpty())

        viewModel.onSelectDate(LocalDate.parse("2026-05-17"))
        advanceUntilIdle()

        val updated = viewModel.state.value.calendar
        assertEquals("今天五一七", updated.selectedEntry?.content)
        assertEquals(
            listOf(LocalDate.parse("2025-05-17"), LocalDate.parse("2024-05-17")),
            updated.previousYearsOnThisDay.map(DiaryEntry::date),
        )

        viewModelJob.cancel()
    }

    @Test
    fun `打开年度总结后载入正文 内容变更经 debounce 后落库 关闭后重置`() = runTest(dispatcher) {
        val annualRepo = FakeAnnualSummaryRepository(
            initialSummaries = listOf(summary(2025, "去年总结")),
        )
        val viewModel = createViewModel(FakeDiaryRepository(), annualRepo)
        advanceUntilIdle()

        viewModel.onOpenSummary(2025)
        advanceUntilIdle()
        assertEquals(2025, viewModel.state.value.annual.selectedYear)
        assertEquals("去年总结", viewModel.state.value.annual.editingContent)

        viewModel.onSummaryContentChanged("去年总结的修订版")
        // 在 debounce 触发前，状态应为 Saving
        runCurrent()
        assertEquals(SaveStatus.Saving, viewModel.state.value.annual.editingSaveStatus)
        // 还未达到 debounce 时间时不应落库
        advanceTimeBy(ReviewViewModel.SUMMARY_SAVE_DEBOUNCE_MS - 50)
        runCurrent()
        assertEquals("去年总结", annualRepo.peek(2025))

        advanceTimeBy(100)
        runCurrent()
        advanceUntilIdle()
        assertEquals("去年总结的修订版", annualRepo.peek(2025))
        assertEquals(SaveStatus.Saved, viewModel.state.value.annual.editingSaveStatus)

        viewModel.onCloseSummary()
        advanceUntilIdle()
        assertNull(viewModel.state.value.annual.selectedYear)
        assertEquals("", viewModel.state.value.annual.editingContent)
        assertEquals(SaveStatus.Idle, viewModel.state.value.annual.editingSaveStatus)

        viewModelJob.cancel()
    }

    @Test
    fun `搜索关键词 年份与 recordedOnly 改变后通过 SearchUseCase 过滤`() = runTest(dispatcher) {
        val entries = listOf(
            entry("2025-05-17", "散步和读书"),
            entry("2026-05-15", "午后散步"),
            entry("2026-05-16", "工作复盘"),
            entry("2026-05-17", "继续散步"),
            entry("2026-05-18", "   "), // 空白记录
        )
        val viewModel = createViewModel(
            FakeDiaryRepository(initialEntries = entries),
            FakeAnnualSummaryRepository(),
        )
        advanceUntilIdle()

        // 可用年份按倒序
        assertEquals(listOf(2026, 2025), viewModel.state.value.search.availableYears)

        viewModel.onKeywordChanged("散步")
        advanceTimeBy(ReviewViewModel.KEYWORD_DEBOUNCE_MS + 50)
        runCurrent()
        advanceUntilIdle()

        val afterKeyword = viewModel.state.value.search.results.map(DiaryEntry::date)
        assertEquals(
            listOf(
                LocalDate.parse("2026-05-17"),
                LocalDate.parse("2026-05-15"),
                LocalDate.parse("2025-05-17"),
            ),
            afterKeyword,
        )

        viewModel.onYearFilterChanged(2026)
        advanceUntilIdle()
        assertEquals(
            listOf(LocalDate.parse("2026-05-17"), LocalDate.parse("2026-05-15")),
            viewModel.state.value.search.results.map(DiaryEntry::date),
        )

        viewModel.onYearFilterChanged(null)
        viewModel.onKeywordChanged("")
        advanceTimeBy(ReviewViewModel.KEYWORD_DEBOUNCE_MS + 50)
        runCurrent()
        advanceUntilIdle()
        // recordedOnly 默认 false（搜索是纯搜索），空白记录应被包含。
        val withBlanks = viewModel.state.value.search.results.map(DiaryEntry::date)
        assertTrue(LocalDate.parse("2026-05-18") in withBlanks)

        viewModel.onToggleRecordedOnly(true)
        advanceUntilIdle()
        // 切到 recordedOnly = true 后空白记录应被过滤。
        val recordedOnly = viewModel.state.value.search.results.map(DiaryEntry::date)
        assertFalse(LocalDate.parse("2026-05-18") in recordedOnly)

        viewModelJob.cancel()
    }

    private fun TestScope.createViewModel(
        diaryRepository: DiaryRepository,
        annualSummaryRepository: AnnualSummaryRepository,
    ): ReviewViewModel = ReviewViewModel(
        diaryRepository = diaryRepository,
        annualSummaryRepository = annualSummaryRepository,
        calendarUseCase = DiaryCalendarUseCase(),
        searchUseCase = SearchDiaryEntriesUseCase(),
        clock = fixedClock,
        scope = CoroutineScope(coroutineContext + viewModelJob),
    )

    private fun entry(date: String, content: String): DiaryEntry = DiaryEntry(
        date = LocalDate.parse(date),
        content = content,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-05-17T00:00:00Z"),
        syncState = SyncState.Synced,
    )

    private fun summary(year: Int, content: String): AnnualSummary = AnnualSummary(
        year = year,
        content = content,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-05-17T00:00:00Z"),
        syncState = SyncState.Synced,
    )

    /** In-memory 假实现，仅覆盖 ReviewViewModel 用到的接口。 */
    private class FakeDiaryRepository(
        initialEntries: List<DiaryEntry> = emptyList(),
    ) : DiaryRepository {
        private val entries = MutableStateFlow(initialEntries)

        override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> =
            entries.map { list -> list.firstOrNull { it.date == date } }

        override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
            entries.map { list ->
                list.filter { YearMonth.from(it.date) == month }
            }

        override fun observeRecordedDates(): Flow<List<LocalDate>> =
            entries.map { list -> list.map(DiaryEntry::date) }

        override fun observeAllEntries(): Flow<List<DiaryEntry>> = entries.asStateFlow()

        override suspend fun getEntry(date: LocalDate): DiaryEntry? =
            entries.value.firstOrNull { it.date == date }

        override suspend fun getEntriesBetween(
            start: LocalDate,
            endInclusive: LocalDate,
        ): List<DiaryEntry> =
            entries.value.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }

        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
            throw UnsupportedOperationException("not used in ReviewViewModel tests")
        }

        override suspend fun deleteEntry(date: LocalDate) = Unit

        override suspend fun searchEntries(
            query: String,
            year: Int?,
            recordedOnly: Boolean,
        ): List<DiaryEntry> = emptyList()

        override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> =
            entries.value
                .filter { it.date.month == date.month && it.date.dayOfMonth == date.dayOfMonth && it.date.isBefore(date) }
                .sortedByDescending(DiaryEntry::date)
    }

    private class FakeAnnualSummaryRepository(
        initialSummaries: List<AnnualSummary> = emptyList(),
    ) : AnnualSummaryRepository {
        private val summaries = MutableStateFlow(initialSummaries)

        override fun observeSummary(year: Int): Flow<AnnualSummary?> =
            summaries.map { list -> list.firstOrNull { it.year == year } }

        override fun observeSummaries(): Flow<List<AnnualSummary>> = summaries.asStateFlow()

        override suspend fun getSummary(year: Int): AnnualSummary? =
            summaries.value.firstOrNull { it.year == year }

        override suspend fun saveSummary(year: Int, content: String): AnnualSummary {
            val now = Instant.parse("2026-05-17T00:00:00Z")
            val updated = AnnualSummary(
                year = year,
                content = content,
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.Pending,
            )
            summaries.value = summaries.value.filterNot { it.year == year } + updated
            return updated
        }

        override suspend fun deleteSummary(year: Int) {
            summaries.value = summaries.value.filterNot { it.year == year }
        }

        fun peek(year: Int): String? = summaries.value.firstOrNull { it.year == year }?.content
    }
}
