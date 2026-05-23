package com.tracediary.app.ui.settings

import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.SameDayEntrySortOrder
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.security.CloudCipherCredentialStore
import com.tracediary.app.data.security.CryptoBox
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.data.sync.SyncObjectKeys
import com.tracediary.app.data.sync.SyncWorkScheduler
import com.tracediary.app.data.sync.decodeDiary
import com.tracediary.app.data.sync.encodeDiary
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.MergeStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.tracediary.app.data.sync.RemoteResult
import com.tracediary.app.data.sync.RemoteStorage
import com.tracediary.app.data.sync.RemoteObject
import com.tracediary.app.data.sync.ErrorCode
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeUserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeUserPreferencesRepository()
        // saveDelayMillis = 0 以避免测试中等待真实延迟
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = FakeSyncMetadataRepository(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsDefaultPreferences() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.dailyReminderEnabled)
        assertEquals("21:00", state.dailyReminderTime)
        assertEquals("00:00", state.dayBoundaryTime)
        assertTrue(state.savePasswordLocally)
        assertFalse(state.syncEnabled)
        assertEquals("aliyun", state.syncProvider)
        assertEquals("TraceDiary/", state.syncDirectory)
        assertEquals(SaveStatus.Idle, state.saveStatus)
        assertEquals("暂无同步记录", state.lastSyncText)
        assertEquals(MergeStrategy.KeepNewer, state.manualSyncStrategy)
    }

    @Test
    fun lastSyncText_reflectsLatestMetadataTime() = runTest(testDispatcher) {
        val metadataRepo = FakeSyncMetadataRepository(
            latest = Instant.parse("2026-05-17T07:30:00Z")
        )
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = metadataRepo,
            zoneId = ZoneId.of("Asia/Shanghai"),
            clock = Clock.fixed(Instant.parse("2026-05-19T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )

        advanceUntilIdle()

        assertEquals("5月17日 15:30", viewModel.uiState.value.lastSyncText)
    }

    @Test
    fun lastSyncText_usesRelativeDateForTodayAndYesterday() = runTest(testDispatcher) {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val metadataRepo = FakeSyncMetadataRepository(
            latest = Instant.parse("2026-05-19T07:30:00Z")
        )
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = metadataRepo,
            zoneId = zoneId,
            clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), zoneId),
        )

        advanceUntilIdle()

        assertEquals("今天 15:30", viewModel.uiState.value.lastSyncText)

        metadataRepo.publishLatest(Instant.parse("2026-05-18T07:30:00Z"))
        advanceUntilIdle()

        assertEquals("昨天 15:30", viewModel.uiState.value.lastSyncText)
    }

    @Test
    fun lastSyncText_keepsYearForPreviousYears() = runTest(testDispatcher) {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val metadataRepo = FakeSyncMetadataRepository(
            latest = Instant.parse("2025-12-31T15:30:00Z")
        )
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = metadataRepo,
            zoneId = zoneId,
            clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), zoneId),
        )

        advanceUntilIdle()

        assertEquals("2025年12月31日 23:30", viewModel.uiState.value.lastSyncText)
    }

    @Test
    fun onToggleReminder_persistsTrueToRepository() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onToggleReminder(true)
        advanceUntilIdle()
        assertTrue(fakeRepo.current().dailyReminderEnabled)
        assertTrue(viewModel.uiState.value.dailyReminderEnabled)
    }

    @Test
    fun onReminderTimeChanged_persistsNewTime() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onReminderTimeChanged("08:30")
        advanceUntilIdle()
        assertEquals("08:30", fakeRepo.current().dailyReminderTime)
        assertEquals("08:30", viewModel.uiState.value.dailyReminderTime)
    }

    @Test
    fun onToggleSavePasswordLocally_persistsFalse() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onToggleSavePasswordLocally(false)
        advanceUntilIdle()
        assertFalse(fakeRepo.current().savePasswordLocally)
    }

    @Test
    fun onToggleSync_persistsTrue() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onToggleSync(true)
        advanceUntilIdle()
        assertTrue(fakeRepo.current().syncEnabled)
    }

    @Test
    fun onToggleSync_trueDoesNotSchedulePeriodicSync() = runTest(testDispatcher) {
        val scheduler = FakeSyncWorkScheduler()
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncScheduler = scheduler,
            syncMetadataRepository = FakeSyncMetadataRepository(),
        )
        advanceUntilIdle()

        viewModel.onToggleSync(true)
        advanceUntilIdle()

        assertTrue(fakeRepo.current().syncEnabled)
        assertEquals(0, scheduler.scheduleCalls)
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun onToggleSync_falseCancelsExistingPeriodicSync() = runTest(testDispatcher) {
        val scheduler = FakeSyncWorkScheduler()
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncScheduler = scheduler,
            syncMetadataRepository = FakeSyncMetadataRepository(),
        )
        fakeRepo.setSyncEnabled(true)
        advanceUntilIdle()

        viewModel.onToggleSync(false)
        advanceUntilIdle()

        assertFalse(fakeRepo.current().syncEnabled)
        assertEquals(0, scheduler.scheduleCalls)
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun onManualSyncStrategyChanged_updatesState() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onManualSyncStrategyChanged(MergeStrategy.SkipExisting)

        assertEquals(MergeStrategy.SkipExisting, viewModel.uiState.value.manualSyncStrategy)
    }

    @Test
    fun onMergeAll_usesSelectedManualStrategy() = runTest(testDispatcher) {
        val date = LocalDate.of(2026, 5, 20)
        val localEntry = DiaryEntry(
            date = date,
            content = "本机内容",
            createdAt = Instant.parse("2026-05-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-20T01:00:00Z"),
            syncState = SyncState.LocalOnly,
        )
        val remoteEntry = localEntry.copy(
            content = "云端内容",
            updatedAt = Instant.parse("2026-05-20T02:00:00Z"),
        )
        val key = CryptoBox.deriveKey("password", "salt-123456789012".toByteArray())
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, key),
            lastModified = remoteEntry.updatedAt,
        )
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val syncEngine = SyncEngine(
            storageProvider = { storage },
            secureCredentialStore = FakeCredentialStore("password", "salt-123456789012".toByteArray()),
            diaryRepository = diaryRepo,
            annualSummaryRepository = FakeAnnualSummaryRepository(),
            syncMetadataRepository = FakeSyncMetadataRepository(),
            clock = Clock.fixed(Instant.parse("2026-05-20T03:00:00Z"), ZoneOffset.UTC),
        )
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncEngine = syncEngine,
            syncMetadataRepository = FakeSyncMetadataRepository(),
        )
        advanceUntilIdle()

        viewModel.onManualSyncStrategyChanged(MergeStrategy.OverwriteAll)
        viewModel.onMergeAll()
        advanceUntilIdle()

        val status = viewModel.uiState.value.syncOpStatus
        assertTrue(status is SyncOpStatus.Done)
        assertEquals(1, (status as SyncOpStatus.Done).summary.uploaded)
        assertEquals("本机内容", decodeDiary(storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.body, key).content)
    }

    @Test
    fun onSyncProviderChanged_persistsProvider() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncProviderChanged("tencent")
        advanceUntilIdle()
        assertEquals("tencent", fakeRepo.current().syncProvider)
    }

    @Test
    fun onSameDayEntrySortOrderChanged_persistsPreference() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSameDayEntrySortOrderChanged(SameDayEntrySortOrder.EarliestFirst)
        advanceUntilIdle()

        assertEquals(SameDayEntrySortOrder.EarliestFirst, fakeRepo.current().sameDayEntrySortOrder)
        assertEquals(SameDayEntrySortOrder.EarliestFirst, viewModel.uiState.value.sameDayEntrySortOrder)
    }

    @Test
    fun onSyncProviderChanged_keepsDraftPerProvider() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncDisplayNameChanged("阿里云空间")
        viewModel.onSyncBucketChanged("aliyun-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("aliyun-dir/")
        viewModel.onSyncAccessKeyIdChanged("aliyun-ak")
        viewModel.onSyncAccessKeySecretChanged("aliyun-secret")

        viewModel.onSyncProviderChanged("tencent")
        advanceUntilIdle()

        assertEquals("tencent", viewModel.uiState.value.syncProvider)
        assertEquals("", viewModel.uiState.value.syncBucket)

        viewModel.onSyncDisplayNameChanged("腾讯云空间")
        viewModel.onSyncBucketChanged("tencent-bucket")
        viewModel.onSyncRegionChanged("ap-guangzhou")
        viewModel.onSyncDirectoryChanged("tencent-dir/")
        viewModel.onSyncAccessKeyIdChanged("tencent-ak")
        viewModel.onSyncAccessKeySecretChanged("tencent-secret")

        viewModel.onSyncProviderChanged("aliyun")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("aliyun", state.syncProvider)
        assertEquals("阿里云空间", state.syncDisplayName)
        assertEquals("aliyun-bucket", state.syncBucket)
        assertEquals("cn-hangzhou", state.syncRegion)
        assertEquals("aliyun-dir/", state.syncDirectory)
        assertEquals("aliyun-ak", state.syncAccessKeyId)
        assertEquals("aliyun-secret", state.syncAccessKeySecret)
    }

    @Test
    fun onSaveSyncConfiguration_persistsNonSensitiveFieldsBeforePing() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncBucketChanged("my-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("diary/")
        viewModel.onSyncDisplayNameChanged("我的日记")

        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()

        // 配置在发起真实 ping 之前就已经写入 DataStore，单测不依赖网络结果。
        assertEquals("my-bucket", fakeRepo.current().syncBucket)
        assertEquals("cn-hangzhou", fakeRepo.current().syncRegion)
        assertEquals("diary/", fakeRepo.current().syncDirectory)
        assertEquals("我的日记", fakeRepo.current().syncDisplayName)
        // ping 在 Dispatchers.IO 上跑真实 OkHttp，testDispatcher 推不动；
        // 仅断言 saveStatus 至少离开了 Idle，进入了"保存中或已结束"的轨道。
        assertTrue(viewModel.uiState.value.saveStatus != SaveStatus.Idle)
    }

    @Test
    fun onSaveSyncConfiguration_successSetsSavedStatus() = runTest(testDispatcher) {
        val storage = FakeRemoteStorage(pingResult = RemoteResult.Ok(Unit))
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = FakeSyncMetadataRepository(),
            remoteStorageFactory = { storage },
        )
        advanceUntilIdle()

        viewModel.onSyncBucketChanged("my-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("diary/")
        viewModel.onSyncDisplayNameChanged("我的日记")
        viewModel.onSyncAccessKeyIdChanged("AKID123")
        viewModel.onSyncAccessKeySecretChanged("secret456")

        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)
        assertEquals(null, viewModel.uiState.value.saveError)
    }

    @Test
    fun onSaveSyncConfiguration_failureSetsFailedStatusAndMessage() = runTest(testDispatcher) {
        val storage = FakeRemoteStorage(
            pingResult = RemoteResult.Err(ErrorCode.AUTH, "AK/SK 无效")
        )
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = FakeSyncMetadataRepository(),
            remoteStorageFactory = { storage },
        )
        advanceUntilIdle()

        viewModel.onSyncBucketChanged("my-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("diary/")
        viewModel.onSyncDisplayNameChanged("我的日记")
        viewModel.onSyncAccessKeyIdChanged("AKID123")
        viewModel.onSyncAccessKeySecretChanged("secret456")

        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()

        assertEquals(SaveStatus.Failed, viewModel.uiState.value.saveStatus)
        assertEquals("AK/SK 无效", viewModel.uiState.value.saveError)
    }

    @Test
    fun editingSyncFields_resetsSaveStatusToIdle() = runTest(testDispatcher) {
        val storage = FakeRemoteStorage(pingResult = RemoteResult.Ok(Unit))
        viewModel = SettingsViewModel(
            repository = fakeRepo,
            saveDelayMillis = 0L,
            syncMetadataRepository = FakeSyncMetadataRepository(),
            remoteStorageFactory = { storage },
        )
        advanceUntilIdle()

        viewModel.onSyncBucketChanged("my-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("diary/")
        viewModel.onSyncDisplayNameChanged("我的日记")
        viewModel.onSyncAccessKeyIdChanged("AKID123")
        viewModel.onSyncAccessKeySecretChanged("secret456")
        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)

        viewModel.onSyncBucketChanged("new-bucket")
        advanceUntilIdle()

        assertEquals(SaveStatus.Idle, viewModel.uiState.value.saveStatus)
        assertEquals(null, viewModel.uiState.value.saveError)
    }

    @Test
    fun sensitiveFields_notPersistedToRepository() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncAccessKeyIdChanged("AKID123")
        viewModel.onSyncAccessKeySecretChanged("secret456")
        advanceUntilIdle()

        // 内存 state 有值
        assertEquals("AKID123", viewModel.uiState.value.syncAccessKeyId)
        assertEquals("secret456", viewModel.uiState.value.syncAccessKeySecret)

        // 仓库中无对应字段（UserPreferences 不含 accessKey）
        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()
        // saveSyncConfiguration 把非敏感字段写入了 DataStore；
        // 这里仅断言敏感字段不会出现在 UserPreferences 里。
        val current = fakeRepo.current()
        // UserPreferences 里没有 AK/SK 字段，能编译通过即说明类型上排除了泄漏。
        assertFalse(current.toString().contains("AKID123"))
        assertFalse(current.toString().contains("secret456"))
    }

    @Test
    fun dayBoundaryTime_change_isPersistedAndNormalized() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onDayBoundaryTimeChanged("02:18")
        advanceUntilIdle()

        assertEquals("02:00", fakeRepo.current().dayBoundaryTime)
        assertEquals("02:00", viewModel.uiState.value.dayBoundaryTime)

        viewModel.onDayBoundaryTimeChanged("08:45")
        advanceUntilIdle()

        assertEquals("05:00", fakeRepo.current().dayBoundaryTime)
        assertEquals("05:00", viewModel.uiState.value.dayBoundaryTime)
    }

    // ---- Fake 实现 ----

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val _prefs = MutableStateFlow(UserPreferences())
        override val preferences: Flow<UserPreferences> = _prefs

        fun current(): UserPreferences = _prefs.value

        override suspend fun setReminderEnabled(value: Boolean) {
            _prefs.update { it.copy(dailyReminderEnabled = value) }
        }

        override suspend fun setReminderTime(time: String) {
            _prefs.update { it.copy(dailyReminderTime = time) }
        }

        override suspend fun setDayBoundaryTime(time: String) {
            _prefs.update { it.copy(dayBoundaryTime = time) }
        }

        override suspend fun setSavePasswordLocally(value: Boolean) {
            _prefs.update { it.copy(savePasswordLocally = value) }
        }

        override suspend fun setSyncEnabled(value: Boolean) {
            _prefs.update { it.copy(syncEnabled = value) }
        }

        override suspend fun setSyncProvider(value: String) {
            _prefs.update { it.copy(syncProvider = value) }
        }

        override suspend fun setSyncBucket(value: String) {
            _prefs.update { it.copy(syncBucket = value) }
        }

        override suspend fun setSyncRegion(value: String) {
            _prefs.update { it.copy(syncRegion = value) }
        }

        override suspend fun setSyncDirectory(value: String) {
            _prefs.update { it.copy(syncDirectory = value) }
        }

        override suspend fun setSameDayEntrySortOrder(value: SameDayEntrySortOrder) {
            _prefs.update { it.copy(sameDayEntrySortOrder = value) }
        }

        override suspend fun setSyncDisplayName(value: String) {
            _prefs.update { it.copy(syncDisplayName = value) }
        }

        override suspend fun saveSyncConfiguration(
            provider: String,
            bucket: String,
            region: String,
            directory: String,
            displayName: String,
        ) {
            _prefs.update {
                it.copy(
                    syncProvider = provider,
                    syncBucket = bucket,
                    syncRegion = region,
                    syncDirectory = directory,
                    syncDisplayName = displayName
                )
            }
        }
    }

    private class FakeSyncMetadataRepository(
        latest: Instant? = null
    ) : SyncMetadataRepository {
        private val latestFlow = MutableStateFlow(latest)

        override fun observeMetadata(objectKey: String): Flow<SyncMetadata?> = flowOf(null)

        override fun observeLatestLastSyncedAt(): Flow<Instant?> = latestFlow

        fun publishLatest(value: Instant?) {
            latestFlow.value = value
        }

        override suspend fun getMetadata(objectKey: String): SyncMetadata? = null

        override suspend fun saveMetadata(metadata: SyncMetadata) = Unit

        override suspend fun deleteMetadata(objectKey: String) = Unit
    }

    private class FakeSyncWorkScheduler : SyncWorkScheduler {
        var scheduleCalls = 0
        var cancelCalls = 0

        override fun schedule() {
            scheduleCalls++
        }

        override fun cancel() {
            cancelCalls++
        }
    }

    private class FakeCredentialStore(
        private var password: String?,
        salt: ByteArray,
    ) : CloudCipherCredentialStore {
        private var currentSalt = salt.copyOf()

        override fun saveLocalPassword(value: String?) {
            password = value
        }

        override fun loadLocalPassword(): String? = password

        override fun loadOrCreateCloudCipherSalt(): ByteArray = currentSalt.copyOf()

        override fun rotateCloudCipherSalt(): ByteArray = currentSalt

        override fun saveCloudCipherSalt(bytes: ByteArray) {
            currentSalt = bytes.copyOf()
        }
    }

    private class FakeDiaryRepository(
        initial: List<DiaryEntry> = emptyList(),
    ) : DiaryRepository {
        val store: MutableMap<LocalDate, DiaryEntry> = LinkedHashMap<LocalDate, DiaryEntry>().apply {
            initial.forEach { put(it.date, it) }
        }
        private val all = MutableStateFlow(store.values.toList())

        private fun publish() {
            all.value = store.values.toList()
        }

        override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> = flowOf(store[date])

        override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
            flowOf(store.values.filter { YearMonth.from(it.date) == month })

        override fun observeRecordedDates(): Flow<List<LocalDate>> = flowOf(store.keys.toList())

        override fun observeAllEntries(): Flow<List<DiaryEntry>> = all.asStateFlow()

        override suspend fun getEntry(date: LocalDate): DiaryEntry? = store[date]

        override suspend fun getEntriesBetween(
            start: LocalDate,
            endInclusive: LocalDate,
        ): List<DiaryEntry> = store.values.filter {
            !it.date.isBefore(start) && !it.date.isAfter(endInclusive)
        }

        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
            val existing = store[date]
            val entry = DiaryEntry(
                date = date,
                content = content,
                createdAt = existing?.createdAt ?: Instant.parse("2026-05-20T03:00:00Z"),
                updatedAt = Instant.parse("2026-05-20T03:00:00Z"),
                syncState = SyncState.LocalOnly,
            )
            store[date] = entry
            publish()
            return entry
        }

        override suspend fun deleteEntry(date: LocalDate) {
            store.remove(date)
            publish()
        }

        override suspend fun searchEntries(
            query: String,
            year: Int?,
            recordedOnly: Boolean,
        ): List<DiaryEntry> = emptyList()

        override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> = emptyList()

        override suspend fun setSyncState(date: LocalDate, state: SyncState) {
            store[date]?.let { store[date] = it.copy(syncState = state) }
            publish()
        }
    }

    private class FakeAnnualSummaryRepository(
        initial: List<AnnualSummary> = emptyList(),
    ) : AnnualSummaryRepository {
        private val store: MutableMap<Int, AnnualSummary> = LinkedHashMap<Int, AnnualSummary>().apply {
            initial.forEach { put(it.year, it) }
        }
        private val all = MutableStateFlow(store.values.toList())

        private fun publish() {
            all.value = store.values.toList()
        }

        override fun observeSummary(year: Int): Flow<AnnualSummary?> = flowOf(store[year])

        override fun observeSummaries(): Flow<List<AnnualSummary>> = all.asStateFlow()

        override suspend fun getSummary(year: Int): AnnualSummary? = store[year]

        override suspend fun saveSummary(year: Int, content: String): AnnualSummary {
            val existing = store[year]
            val summary = AnnualSummary(
                year = year,
                content = content,
                createdAt = existing?.createdAt ?: Instant.parse("2026-05-20T03:00:00Z"),
                updatedAt = Instant.parse("2026-05-20T03:00:00Z"),
                syncState = SyncState.LocalOnly,
            )
            store[year] = summary
            publish()
            return summary
        }

        override suspend fun deleteSummary(year: Int) {
            store.remove(year)
            publish()
        }

        override suspend fun setSyncState(year: Int, state: SyncState) {
            store[year]?.let { store[year] = it.copy(syncState = state) }
            publish()
        }
    }

    private class FakeRemoteStorage(
        private val pingResult: RemoteResult<Unit> = RemoteResult.Ok(Unit),
    ) : RemoteStorage {
        data class StoredObject(
            val key: String,
            val body: ByteArray,
            val etag: String,
            val lastModified: Instant?,
        )

        val objects: MutableMap<String, StoredObject> = LinkedHashMap()
        private var etagSeq = 0
        val lastIfMatchEtags: MutableList<String?> = mutableListOf()

        fun seed(key: String, body: ByteArray, lastModified: Instant?) {
            etagSeq++
            objects[key] = StoredObject(key, body, "etag-$etagSeq", lastModified)
        }

        override suspend fun put(
            key: String,
            body: ByteArray,
            contentType: String,
            ifMatchEtag: String?,
        ): RemoteResult<RemoteObject> {
            lastIfMatchEtags += ifMatchEtag
            val current = objects[key]
            if (ifMatchEtag != null && current != null && current.etag != ifMatchEtag) {
                return RemoteResult.Err(ErrorCode.CONFLICT, "etag mismatch")
            }
            etagSeq++
            val now = Instant.parse("2026-05-20T03:00:00Z")
            val stored = StoredObject(key, body, "etag-$etagSeq", now)
            objects[key] = stored
            return RemoteResult.Ok(RemoteObject(key, stored.etag, now, body.size.toLong()))
        }

        override suspend fun get(key: String): RemoteResult<ByteArray> =
            objects[key]?.body?.let { RemoteResult.Ok(it) }
                ?: RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")

        override suspend fun head(key: String): RemoteResult<RemoteObject> {
            val obj = objects[key] ?: return RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")
            return RemoteResult.Ok(RemoteObject(obj.key, obj.etag, obj.lastModified, obj.body.size.toLong()))
        }

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

        override suspend fun ping(): RemoteResult<Unit> = pingResult
    }

}
