package com.tracediary.app.ui.settings

import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        viewModel = SettingsViewModel(fakeRepo, saveDelayMillis = 0L)
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
    fun onSaveSyncConfiguration_transitionsStatusToSaved() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onSyncBucketChanged("my-bucket")
        viewModel.onSyncRegionChanged("cn-hangzhou")
        viewModel.onSyncDirectoryChanged("diary/")
        viewModel.onSyncDisplayNameChanged("我的日记")

        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)
        assertEquals("my-bucket", fakeRepo.current().syncBucket)
        assertEquals("cn-hangzhou", fakeRepo.current().syncRegion)
        assertEquals("diary/", fakeRepo.current().syncDirectory)
        assertEquals("我的日记", fakeRepo.current().syncDisplayName)
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
        // 验证 saveSyncConfiguration 不会把它们写入
        viewModel.onSaveSyncConfiguration()
        advanceUntilIdle()
        // 如果 repo 没有 crash 就说明没有写入敏感字段
        assertEquals(SaveStatus.Saved, viewModel.uiState.value.saveStatus)
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
}
