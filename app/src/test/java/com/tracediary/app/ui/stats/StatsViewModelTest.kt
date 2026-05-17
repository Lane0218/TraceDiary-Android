package com.tracediary.app.ui.stats

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryStatisticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private lateinit var repository: FakeDiaryRepository
    private val useCase = DiaryStatisticsUseCase()
    private val today: LocalDate = LocalDate.parse("2026-05-17")
    private val clock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeDiaryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始化后 statistics 不为 null 且 availableYears 至少含本年`() = runTest {
        repository.emit(
            listOf(
                entry("2026-05-17", "今天"),
                entry("2025-12-31", "去年"),
            ),
        )

        val viewModel = StatsViewModel(repository, useCase, clock)
        val state = viewModel.uiState.awaitLoaded()

        assertFalse(state.isLoading)
        assertNotNull(state.statistics)
        assertEquals(today.year, state.selectedYear)
        assertEquals(today.year, state.selectedTrendYear)
        assertTrue(state.availableYears.contains(today.year))
        assertTrue(state.availableYears.contains(2025))
        // 倒序
        assertEquals(state.availableYears.sortedDescending(), state.availableYears)
    }

    @Test
    fun `仓库新增 entry 后 statistics 自动更新`() = runTest {
        repository.emit(listOf(entry("2026-05-17", "今天")))
        val viewModel = StatsViewModel(repository, useCase, clock)
        val before = viewModel.uiState.awaitLoaded()
        assertEquals(1, before.statistics!!.totalEntries)

        repository.emit(
            listOf(
                entry("2026-05-17", "今天"),
                entry("2026-05-16", "昨天"),
            ),
        )

        val after = viewModel.uiState.awaitState { it.statistics?.totalEntries == 2 }
        assertEquals(2, after.statistics!!.totalEntries)
        assertEquals(2, after.statistics.currentStreakDays)
    }

    @Test
    fun `onSelectYear 切换后 yearWordCount 与热力图来自该年`() = runTest {
        repository.emit(
            listOf(
                entry("2024-06-01", "abcd"),
                entry("2024-06-02", "efgh"),
                entry("2026-05-17", "今天"),
            ),
        )
        val viewModel = StatsViewModel(repository, useCase, clock)
        viewModel.uiState.awaitLoaded()

        viewModel.onSelectYear(2024)

        val state = viewModel.uiState.awaitState { it.selectedYear == 2024 }
        val stats = state.statistics!!
        assertEquals(2024, state.selectedYear)
        assertEquals(2024, state.selectedTrendYear) // 默认联动
        assertEquals(8, stats.yearWordCount)
        assertEquals(2, stats.annualRecordCount)
        // 闰年 2024 共 366 天
        assertEquals(366, stats.yearlyHeatmap.size)
        assertTrue(stats.yearlyHeatmap.all { it.date.year == 2024 })
    }

    @Test
    fun `onSelectTrendYear 单独切换 trend year`() = runTest {
        repository.emit(
            listOf(
                entry("2024-06-01", "abcd"),
                entry("2024-06-02", "efgh"),
                entry("2026-05-17", "今天"),
            ),
        )
        val viewModel = StatsViewModel(repository, useCase, clock)
        viewModel.uiState.awaitLoaded()

        viewModel.onSelectTrendYear(2024)
        val state = viewModel.uiState.awaitState { it.selectedTrendYear == 2024 }
        val stats = state.statistics!!

        // selectedYear 仍为今年，热力图属于今年
        assertEquals(today.year, state.selectedYear)
        assertEquals(2024, state.selectedTrendYear)
        assertTrue(stats.yearlyHeatmap.all { it.date.year == today.year })
        // 趋势卡片来自 2024 年
        val juneTrend = stats.monthlyWordTrend.single { it.yearMonth == YearMonth.of(2024, 6) }
        assertEquals(8, juneTrend.wordCount)
        assertEquals(12, stats.monthlyWordTrend.size)
        assertTrue(stats.monthlyWordTrend.all { it.yearMonth.year == 2024 })
    }

    private fun entry(
        date: String,
        content: String,
        updatedAt: Instant = Instant.parse("2026-05-17T00:00:00Z"),
    ): DiaryEntry = DiaryEntry(
        date = LocalDate.parse(date),
        content = content,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = updatedAt,
        syncState = SyncState.Synced,
    )

    private suspend fun StateFlow<StatsUiState>.awaitLoaded(): StatsUiState =
        dropWhile { it.isLoading || it.statistics == null }.first()

    private suspend fun StateFlow<StatsUiState>.awaitState(
        predicate: (StatsUiState) -> Boolean,
    ): StatsUiState = dropWhile { it.isLoading || it.statistics == null || !predicate(it) }.first()

    /**
     * 仅实现统计页测试需要的方法；其他方法抛出，避免被误用。
     */
    private class FakeDiaryRepository : DiaryRepository {
        private val entriesFlow = MutableStateFlow<List<DiaryEntry>>(emptyList())

        fun emit(entries: List<DiaryEntry>) {
            entriesFlow.value = entries
        }

        override fun observeAllEntries(): Flow<List<DiaryEntry>> = entriesFlow.asStateFlow()

        override fun observeEntry(date: LocalDate) = unsupported()
        override fun observeEntriesInMonth(month: YearMonth) = unsupported()
        override fun observeRecordedDates() = unsupported()
        override suspend fun getEntry(date: LocalDate): DiaryEntry? = unsupported()
        override suspend fun getEntriesBetween(start: LocalDate, endInclusive: LocalDate) = unsupported()
        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry = unsupported()
        override suspend fun deleteEntry(date: LocalDate) = unsupported()
        override suspend fun searchEntries(query: String, year: Int?, recordedOnly: Boolean) = unsupported()
        override suspend fun getSameMonthDayEntries(date: LocalDate) = unsupported()

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("FakeDiaryRepository: 仅支持 observeAllEntries")
    }
}
