package com.tracediary.app.ui.review

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
import com.tracediary.app.data.settings.SameDayEntrySortOrder
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
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
import kotlinx.coroutines.flow.update
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
    fun `初始 Tab 默认为搜索`() = runTest(dispatcher) {
        val repo = FakeDiaryRepository(initialEntries = listOf(entry("2026-05-17", "今天")))
        val viewModel = createViewModel(repo, FakeAnnualSummaryRepository())
        advanceUntilIdle()

        assertEquals(ReviewTab.Search, viewModel.state.value.selectedTab)

        viewModelJob.cancel()
    }

    @Test
    fun `切换 Tab 后 selectedTab 同步更新`() = runTest(dispatcher) {
        val repo = FakeDiaryRepository()
        val viewModel = createViewModel(repo, FakeAnnualSummaryRepository())
        advanceUntilIdle()

        viewModel.onSelectTab(ReviewTab.AnnualSummary)
        assertEquals(ReviewTab.AnnualSummary, viewModel.state.value.selectedTab)

        viewModel.onSelectTab(ReviewTab.Search)
        assertEquals(ReviewTab.Search, viewModel.state.value.selectedTab)

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

    @Test
    fun `writable years follow application year`() = runTest(dispatcher) {
        val repo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val viewModel = ReviewViewModel(
            diaryRepository = repo,
            annualSummaryRepository = annualRepo,
            calendarUseCase = DiaryCalendarUseCase(),
            searchUseCase = SearchDiaryEntriesUseCase(),
            clock = fixedClock,
            initialDate = LocalDate.of(2025, 12, 31),
            scope = CoroutineScope(coroutineContext + viewModelJob),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.annual.writableYears.first() <= 2025)
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

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val prefs = MutableStateFlow(UserPreferences())
        override val preferences: Flow<UserPreferences> = prefs

        override suspend fun setReminderEnabled(value: Boolean) {
            prefs.update { it.copy(dailyReminderEnabled = value) }
        }

        override suspend fun setReminderTime(time: String) {
            prefs.update { it.copy(dailyReminderTime = time) }
        }

        override suspend fun setDayBoundaryTime(time: String) {
            prefs.update { it.copy(dayBoundaryTime = time) }
        }

        override suspend fun setSavePasswordLocally(value: Boolean) {
            prefs.update { it.copy(savePasswordLocally = value) }
        }

        override suspend fun setSyncEnabled(value: Boolean) {
            prefs.update { it.copy(syncEnabled = value) }
        }

        override suspend fun setSyncProvider(value: String) {
            prefs.update { it.copy(syncProvider = value) }
        }

        override suspend fun setSyncDisplayName(value: String) {
            prefs.update { it.copy(syncDisplayName = value) }
        }

        override suspend fun setSyncBucket(value: String) {
            prefs.update { it.copy(syncBucket = value) }
        }

        override suspend fun setSyncRegion(value: String) {
            prefs.update { it.copy(syncRegion = value) }
        }

        override suspend fun setSyncDirectory(value: String) {
            prefs.update { it.copy(syncDirectory = value) }
        }

        override suspend fun setSameDayEntrySortOrder(value: SameDayEntrySortOrder) {
            prefs.update { it.copy(sameDayEntrySortOrder = value) }
        }

        override suspend fun saveSyncConfiguration(
            provider: String,
            bucket: String,
            region: String,
            directory: String,
            displayName: String,
        ) {
            prefs.update {
                it.copy(
                    syncProvider = provider,
                    syncBucket = bucket,
                    syncRegion = region,
                    syncDirectory = directory,
                    syncDisplayName = displayName,
                )
            }
        }
    }
}
