package com.tracediary.app.ui.today

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class TodayViewModelTest {

    private val fixedToday: LocalDate = LocalDate.of(2026, 5, 15)
    private val fixedClock: Clock = Clock.fixed(
        fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(),
        ZoneOffset.UTC,
    )

    private lateinit var dispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var repository: FakeDiaryRepository

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repository = FakeDiaryRepository(fixedClock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads existing entry for today and exposes content`() = runTest(dispatcher) {
        val existing = repository.upsert(fixedToday, "你好今天")
        val viewModel = createViewModel()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(fixedToday, state.selectedDate)
        assertEquals(existing.content, state.content)
        assertEquals(existing.content.length, state.wordCount)
        // 已落库的条目，加载后应展示“已保存”，让用户离开后回来仍能确认保存状态。
        assertEquals(SaveStatus.Saved, state.saveStatus)
        assertEquals(7, state.weekDates.size)
        assertTrue(state.weekDates.contains(fixedToday))
        assertEquals("2026年5月", state.displayMonthLabel)
        assertEquals(SyncState.Pending, state.currentEntrySyncState)
    }

    @Test
    fun `onContentChanged debounces auto save and toggles status`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onContentChanged("第一段")
        // 立即处于 Saving，但还没有落库
        assertEquals(SaveStatus.Saving, viewModel.uiState.value.saveStatus)
        assertNull(repository.snapshot(fixedToday))

        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS)
        advanceUntilIdle()

        val saved = repository.snapshot(fixedToday)
        assertEquals("第一段", saved?.content)
        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)
        assertEquals(3, viewModel.uiState.value.wordCount)
    }

    @Test
    fun `rapid edits collapse into single save after debounce`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onContentChanged("a")
        advanceTimeBy(100)
        viewModel.onContentChanged("ab")
        advanceTimeBy(100)
        viewModel.onContentChanged("abc")
        advanceUntilIdle() // 此时未到达 debounce 末尾，但 advanceUntilIdle 会一直推进直到任务空闲

        // advanceUntilIdle 会执行所有 delay，因此最终会落库一次
        assertEquals("abc", repository.snapshot(fixedToday)?.content)
        assertEquals(1, repository.saveInvocationCount)
        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun `onSelectDate flushes pending content before loading new date`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onContentChanged("尚未保存的内容")
        // 不推进到 debounce 完成，直接切换日期
        val targetDate = fixedToday.minusDays(1)
        viewModel.onSelectDate(targetDate)
        advanceUntilIdle()

        // 切换前的内容必须已经持久化到 fixedToday
        assertEquals("尚未保存的内容", repository.snapshot(fixedToday)?.content)

        val state = viewModel.uiState.value
        assertEquals(targetDate, state.selectedDate)
        // 新日期没有任何记录，content 应该重置为空
        assertEquals("", state.content)
        assertEquals(0, state.wordCount)
        assertEquals(SaveStatus.Idle, state.saveStatus)
        assertTrue(state.weekDates.contains(targetDate))
    }

    @Test
    fun `word count updates immediately on content change`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onContentChanged("一二三")
        assertEquals(3, viewModel.uiState.value.wordCount)

        viewModel.onContentChanged("一二三四五")
        assertEquals(5, viewModel.uiState.value.wordCount)
    }

    @Test
    fun `previousYearsOnThisDay loaded from repository on init`() = runTest(dispatcher) {
        repository.upsert(LocalDate.of(2025, 5, 15), "去年今天")
        repository.upsert(LocalDate.of(2024, 5, 15), "前年今天")
        repository.upsert(LocalDate.of(2024, 5, 16), "不是同月日，应被忽略")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val previous = viewModel.uiState.value.previousYearsOnThisDay
        assertEquals(2, previous.size)
        assertEquals(LocalDate.of(2025, 5, 15), previous[0].date)
        assertEquals(LocalDate.of(2024, 5, 15), previous[1].date)
    }

    @Test
    fun `onPreviousWeek shifts week dates without changing selectedDate`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val originalSelected = viewModel.uiState.value.selectedDate
        val originalFirst = viewModel.uiState.value.weekDates.first()

        viewModel.onPreviousWeek()

        val state = viewModel.uiState.value
        assertEquals(originalSelected, state.selectedDate)
        assertEquals(originalFirst.minusDays(7), state.weekDates.first())
    }

    private fun TestScope.createViewModel(): TodayViewModel {
        return TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            clock = fixedClock,
            ioDispatcher = dispatcher,
        )
    }
}

/**
 * 内存版假 DiaryRepository，仅覆盖 ViewModel 实际调用的方法。
 */
private class FakeDiaryRepository(private val clock: Clock) : DiaryRepository {

    private val store = MutableStateFlow<Map<LocalDate, DiaryEntry>>(emptyMap())
    var saveInvocationCount: Int = 0
        private set

    fun upsert(date: LocalDate, content: String): DiaryEntry {
        val now = Instant.now(clock)
        val entry = DiaryEntry(
            date = date,
            content = content,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.Pending,
        )
        store.value = store.value.toMutableMap().apply { put(date, entry) }
        return entry
    }

    fun snapshot(date: LocalDate): DiaryEntry? = store.value[date]

    override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> =
        store.map { it[date] }

    override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
        flowOf(emptyList())

    override fun observeRecordedDates(): Flow<List<LocalDate>> = flowOf(emptyList())

    override fun observeAllEntries(): Flow<List<DiaryEntry>> =
        store.map { map -> map.values.sortedBy(DiaryEntry::date) }

    override suspend fun getEntry(date: LocalDate): DiaryEntry? = store.value[date]

    override suspend fun getEntriesBetween(
        start: LocalDate,
        endInclusive: LocalDate,
    ): List<DiaryEntry> = store.value.values
        .filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }
        .sortedBy(DiaryEntry::date)

    override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
        saveInvocationCount += 1
        val existing = store.value[date]
        val now = Instant.now(clock)
        val entry = DiaryEntry(
            date = date,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = if (existing?.content == content) existing.updatedAt else now,
            syncState = if (existing?.content == content) existing.syncState else SyncState.Pending,
        )
        store.value = store.value.toMutableMap().apply { put(date, entry) }
        return entry
    }

    override suspend fun deleteEntry(date: LocalDate) {
        store.value = store.value.toMutableMap().apply { remove(date) }
    }

    override suspend fun searchEntries(
        query: String,
        year: Int?,
        recordedOnly: Boolean,
    ): List<DiaryEntry> = emptyList()

    override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> =
        store.value.values
            .filter { it.date.isBefore(date) }
            .filter { it.date.month == date.month && it.date.dayOfMonth == date.dayOfMonth }
            .sortedByDescending(DiaryEntry::date)
}
