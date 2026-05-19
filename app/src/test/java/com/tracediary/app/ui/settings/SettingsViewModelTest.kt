package com.tracediary.app.ui.settings

import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.ZoneId

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
        assertTrue(state.savePasswordLocally)
        assertFalse(state.syncEnabled)
        assertEquals("aliyun", state.syncProvider)
        assertEquals("TraceDiary/", state.syncDirectory)
        assertEquals(SaveStatus.Idle, state.saveStatus)
        assertEquals("暂无同步记录", state.lastSyncText)
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
    fun onSyncProviderChanged_persistsProvider() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncProviderChanged("tencent")
        advanceUntilIdle()
        assertEquals("tencent", fakeRepo.current().syncProvider)
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

        override suspend fun setSyncDisplayName(value: String) {
            _prefs.update { it.copy(syncDisplayName = value) }
        }

        override suspend fun saveSyncConfiguration(
            provider: String,
            bucket: String,
            region: String,
            directory: String,
            displayName: String
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

}
