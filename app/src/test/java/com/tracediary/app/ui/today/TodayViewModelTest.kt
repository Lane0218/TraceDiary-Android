package com.tracediary.app.ui.today

import com.tracediary.app.data.security.CloudCipherCredentialStore
import com.tracediary.app.data.settings.CalendarWeekStart
import com.tracediary.app.data.settings.SameDayEntrySortOrder
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.sync.ErrorCode
import com.tracediary.app.data.sync.RemoteObject
import com.tracediary.app.data.sync.RemoteResult
import com.tracediary.app.data.sync.RemoteStorage
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOf
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
        assertEquals(fixedToday, state.calendarAnchorDate)
        assertEquals(7, state.weekDates.size)
        assertTrue(state.weekDates.contains(fixedToday))
        assertEquals("2026年5月", state.displayMonthLabel)
        assertEquals(SyncState.Pending, state.currentEntrySyncState)
        assertEquals(listOf("日", "一", "二", "三", "四", "五", "六"), state.weekHeaders)
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
    fun `saved content triggers single diary upload after sync debounce`() = runTest(dispatcher) {
        val remoteStorage = FakeRemoteStorage()
        val viewModel = createViewModel(syncEnabled = true, localPassword = "pw", remoteStorage = remoteStorage)
        advanceUntilIdle()

        viewModel.onContentChanged("需要同步的内容")
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals("需要同步的内容", repository.snapshot(fixedToday)?.content)
        assertEquals(0, remoteStorage.putCount)

        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SYNC_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals(0, remoteStorage.putCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, remoteStorage.putCount)
        assertEquals(SyncState.Synced, repository.snapshot(fixedToday)?.syncState)
    }

    @Test
    fun `continuous edits only upload latest saved diary once`() = runTest(dispatcher) {
        val remoteStorage = FakeRemoteStorage()
        val viewModel = createViewModel(syncEnabled = true, localPassword = "pw", remoteStorage = remoteStorage)
        advanceUntilIdle()

        viewModel.onContentChanged("第一版")
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        viewModel.onContentChanged("第二版")
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS)
        runCurrent()
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SYNC_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(1, remoteStorage.putCount)
        assertEquals("第二版", repository.snapshot(fixedToday)?.content)
        assertEquals(SyncState.Synced, repository.snapshot(fixedToday)?.syncState)
    }

    @Test
    fun `sync disabled does not trigger auto upload`() = runTest(dispatcher) {
        val remoteStorage = FakeRemoteStorage()
        val viewModel = createViewModel(syncEnabled = false, localPassword = "pw", remoteStorage = remoteStorage)
        advanceUntilIdle()

        viewModel.onContentChanged("只保存不同步")
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS)
        runCurrent()
        advanceTimeBy(TodayViewModel.DEFAULT_AUTO_SYNC_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(0, remoteStorage.putCount)
        assertEquals(SyncState.Pending, repository.snapshot(fixedToday)?.syncState)
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
        repository.upsert(LocalDate.of(2026, 5, 15), "今年今天")
        repository.upsert(LocalDate.of(2024, 5, 16), "不是同月日，应被忽略")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val previous = viewModel.uiState.value.previousYearsOnThisDay
        assertEquals(2, previous.size)
        assertEquals(listOf(LocalDate.of(2025, 5, 15), LocalDate.of(2024, 5, 15)), previous.map(DiaryEntry::date))
    }

    @Test
    fun `same day entries follow preference order and exclude current date`() = runTest(dispatcher) {
        repository.upsert(LocalDate.of(2024, 5, 15), "前年今天")
        repository.upsert(LocalDate.of(2025, 5, 15), "去年今天")
        repository.upsert(LocalDate.of(fixedToday.year, fixedToday.month, fixedToday.dayOfMonth), "今年今天")

        val preferences = FakeUserPreferencesRepository(syncEnabled = false)
        val viewModel = createViewModel(userPreferencesRepository = preferences)
        advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2025, 5, 15), LocalDate.of(2024, 5, 15)),
            viewModel.uiState.value.previousYearsOnThisDay.map(DiaryEntry::date),
        )

        preferences.setSameDayEntrySortOrder(SameDayEntrySortOrder.EarliestFirst)
        advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2024, 5, 15), LocalDate.of(2025, 5, 15)),
            viewModel.uiState.value.previousYearsOnThisDay.map(DiaryEntry::date),
        )
        assertFalse(viewModel.uiState.value.previousYearsOnThisDay.any { it.date == fixedToday })
    }

    @Test
    fun `calendar week start preference updates week dates headers and month grid`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository(syncEnabled = false)
        val viewModel = createViewModel(userPreferencesRepository = preferences)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 10), viewModel.uiState.value.weekDates.first())
        assertEquals(LocalDate.of(2026, 4, 26), viewModel.uiState.value.monthGrid.first().date)

        preferences.setCalendarWeekStart(CalendarWeekStart.Monday)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 5, 11), state.weekDates.first())
        assertEquals(LocalDate.of(2026, 5, 17), state.weekDates.last())
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), state.weekHeaders)
        assertEquals(LocalDate.of(2026, 4, 27), state.monthGrid.first().date)
    }

    @Test
    fun `onJumpToToday resolves current business date on each click`() = runTest(dispatcher) {
        var currentAppDate = fixedToday
        val viewModel = createViewModel(currentAppDate = { currentAppDate })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2024, 5, 15))
        advanceUntilIdle()

        viewModel.onJumpToToday()
        advanceUntilIdle()

        assertEquals(fixedToday, viewModel.uiState.value.selectedDate)

        currentAppDate = fixedToday.plusDays(1)
        viewModel.onJumpToToday()
        advanceUntilIdle()

        assertEquals(fixedToday.plusDays(1), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `initial date follows application day boundary before 2am`() = runTest(dispatcher) {
        val boundaryClock = Clock.fixed(
            LocalDate.of(2026, 5, 17).atTime(1, 30).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )
        val preferences = FakeUserPreferencesRepository(syncEnabled = false, dayBoundaryTime = "02:00")
        val viewModel = TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            userPreferencesRepository = preferences,
            clock = boundaryClock,
            initialDate = LocalDate.of(2026, 5, 16),
            ioDispatcher = dispatcher,
        )

        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 16), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `sync state updates when repository entry changes`() = runTest(dispatcher) {
        repository.upsert(fixedToday, "今天内容", syncState = SyncState.Failed)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SyncState.Failed, viewModel.uiState.value.currentEntrySyncState)

        repository.setSyncState(fixedToday, SyncState.Synced)
        advanceUntilIdle()

        assertEquals(SyncState.Synced, viewModel.uiState.value.currentEntrySyncState)
        assertEquals("今天内容", viewModel.uiState.value.content)
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
        assertEquals(originalSelected.minusDays(7), state.calendarAnchorDate)
        assertEquals(originalFirst.minusDays(7), state.weekDates.first())
    }

    @Test
    fun `month navigation anchors collapsed week to browsed month without changing selectedDate`() = runTest(dispatcher) {
        val initialDate = LocalDate.of(2026, 5, 23)
        val viewModel = createViewModel(initialDate = initialDate)
        advanceUntilIdle()

        viewModel.onToggleMonthExpand()
        viewModel.onPreviousMonth()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.isMonthExpanded)
        assertEquals(initialDate, state.selectedDate)
        assertEquals(LocalDate.of(2026, 4, 23), state.calendarAnchorDate)
        assertEquals("2026年4月", state.displayMonthLabel)
        assertEquals(LocalDate.of(2026, 4, 19), state.weekDates.first())
        assertEquals(LocalDate.of(2026, 4, 25), state.weekDates.last())

        viewModel.onToggleMonthExpand()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertFalse(state.isMonthExpanded)
        assertEquals(initialDate, state.selectedDate)
        assertEquals(LocalDate.of(2026, 4, 23), state.calendarAnchorDate)
        assertEquals("2026年4月", state.displayMonthLabel)
        assertEquals(LocalDate.of(2026, 4, 19), state.weekDates.first())
        assertEquals(LocalDate.of(2026, 4, 25), state.weekDates.last())
    }

    @Test
    fun `select date syncs selectedDate and calendar anchor`() = runTest(dispatcher) {
        val viewModel = createViewModel(initialDate = LocalDate.of(2026, 5, 23))
        advanceUntilIdle()

        viewModel.onToggleMonthExpand()
        viewModel.onPreviousMonth()
        advanceUntilIdle()

        val targetDate = LocalDate.of(2026, 4, 21)
        viewModel.onSelectDate(targetDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetDate, state.selectedDate)
        assertEquals(targetDate, state.calendarAnchorDate)
        assertEquals("2026年4月", state.displayMonthLabel)
        assertTrue(state.weekDates.contains(targetDate))
    }

    private fun TestScope.createViewModel(
        syncEnabled: Boolean = false,
        localPassword: String? = null,
        remoteStorage: FakeRemoteStorage = FakeRemoteStorage(),
        userPreferencesRepository: FakeUserPreferencesRepository = FakeUserPreferencesRepository(syncEnabled),
        initialDate: LocalDate = fixedToday,
        currentAppDate: () -> LocalDate = { initialDate },
    ): TodayViewModel {
        val credentialStore = FakeCredentialStore(localPassword)
        val syncEngine = SyncEngine(
            storageProvider = { remoteStorage },
            diaryRepository = repository,
            annualSummaryRepository = FakeAnnualSummaryRepository(),
            syncMetadataRepository = FakeSyncMetadataRepository(),
            passwordProvider = { credentialStore.loadLocalPassword() },
            clock = fixedClock,
        )
        return TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialStore = credentialStore,
            syncEngine = syncEngine,
            clock = fixedClock,
            currentAppDate = currentAppDate,
            initialDate = initialDate,
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

    fun upsert(
        date: LocalDate,
        content: String,
        syncState: SyncState = SyncState.Pending,
    ): DiaryEntry {
        val now = Instant.now(clock)
        val entry = DiaryEntry(
            date = date,
            content = content,
            createdAt = now,
            updatedAt = now,
            syncState = syncState,
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

    override suspend fun setSyncState(date: LocalDate, state: SyncState) {
        store.value[date]?.let { existing ->
            store.value = store.value.toMutableMap().apply {
                put(date, existing.copy(syncState = state))
            }
        }
    }

}

private class FakeUserPreferencesRepository(
    syncEnabled: Boolean,
    dayBoundaryTime: String = "00:00",
    calendarWeekStart: CalendarWeekStart = CalendarWeekStart.Sunday,
) : UserPreferencesRepository {
    private val prefs = MutableStateFlow(
        UserPreferences(
            syncEnabled = syncEnabled,
            dayBoundaryTime = dayBoundaryTime,
            calendarWeekStart = calendarWeekStart,
        )
    )
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

    override suspend fun setCalendarWeekStart(value: CalendarWeekStart) {
        prefs.update { it.copy(calendarWeekStart = value) }
    }

    override suspend fun setSyncDisplayName(value: String) {
        prefs.update { it.copy(syncDisplayName = value) }
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

private class FakeCredentialStore(private var password: String?) : CloudCipherCredentialStore {
    override fun saveLocalPassword(value: String?) {
        password = value
    }

    override fun loadLocalPassword(): String? = password
}

private class FakeRemoteStorage : RemoteStorage {
    var putCount = 0
        private set
    private val objects = LinkedHashMap<String, StoredObject>()

    data class StoredObject(
        val key: String,
        val body: ByteArray,
        val etag: String,
        val lastModified: Instant,
    )

    override suspend fun put(
        key: String,
        body: ByteArray,
        contentType: String,
        ifMatchEtag: String?,
    ): RemoteResult<RemoteObject> {
        putCount += 1
        val now = Instant.parse("2026-05-15T00:00:00Z")
        val stored = StoredObject(key, body, "etag-$putCount", now)
        objects[key] = stored
        return RemoteResult.Ok(RemoteObject(key, stored.etag, now, body.size.toLong()))
    }

    override suspend fun get(key: String): RemoteResult<ByteArray> =
        objects[key]?.let { RemoteResult.Ok(it.body) }
            ?: RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")

    override suspend fun head(key: String): RemoteResult<RemoteObject> =
        objects[key]?.let { RemoteResult.Ok(RemoteObject(it.key, it.etag, it.lastModified, it.body.size.toLong())) }
            ?: RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")

    override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> =
        RemoteResult.Ok(
            objects.values
                .filter { it.key.startsWith(prefix) }
                .map { RemoteObject(it.key, it.etag, it.lastModified, it.body.size.toLong()) }
        )

    override suspend fun delete(key: String): RemoteResult<Unit> {
        objects.remove(key)
        return RemoteResult.Ok(Unit)
    }

    override suspend fun ping(): RemoteResult<Unit> = RemoteResult.Ok(Unit)
}

private class FakeAnnualSummaryRepository : AnnualSummaryRepository {
    override fun observeSummary(year: Int): Flow<AnnualSummary?> = flowOf(null)
    override fun observeSummaries(): Flow<List<AnnualSummary>> = flowOf(emptyList())
    override suspend fun getSummary(year: Int): AnnualSummary? = null
    override suspend fun saveSummary(year: Int, content: String): AnnualSummary =
        error("not used")
    override suspend fun deleteSummary(year: Int) = Unit
}

private class FakeSyncMetadataRepository : SyncMetadataRepository {
    override fun observeMetadata(objectKey: String): Flow<SyncMetadata?> = flowOf(null)
    override fun observeLatestLastSyncedAt(): Flow<Instant?> = flowOf(null)
    override suspend fun getMetadata(objectKey: String): SyncMetadata? = null
    override suspend fun saveMetadata(metadata: SyncMetadata) = Unit
    override suspend fun deleteMetadata(objectKey: String) = Unit
}
