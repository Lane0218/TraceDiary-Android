package com.tracediary.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 保存按钮的状态机：用于"保存并验证连接"的反馈。
 */
enum class SaveStatus {
    Idle,
    Saving,
    Saved,
    Failed
}

/**
 * 设置页 UI 状态。包含 DataStore 持久化字段以及仅在内存中保留的敏感字段。
 */
data class SettingsUiState(
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "21:00",
    val savePasswordLocally: Boolean = true,
    val syncEnabled: Boolean = false,
    val syncProvider: String = "aliyun",
    val syncBucket: String = "",
    val syncRegion: String = "",
    val syncDirectory: String = "TraceDiary/",
    val syncDisplayName: String = "",
    // 敏感字段：仅停留在内存。TODO: 接入 EncryptedSharedPreferences / Keystore 后再持久化。
    val syncAccessKeyId: String = "",
    val syncAccessKeySecret: String = "",
    val saveStatus: SaveStatus = SaveStatus.Idle
)

class SettingsViewModel(
    private val repository: UserPreferencesRepository,
    private val saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 收集 DataStore 偏好，与 UI state 保持同步。敏感字段、saveStatus 不被覆盖。
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        dailyReminderEnabled = prefs.dailyReminderEnabled,
                        dailyReminderTime = prefs.dailyReminderTime,
                        savePasswordLocally = prefs.savePasswordLocally,
                        syncEnabled = prefs.syncEnabled,
                        syncProvider = prefs.syncProvider,
                        syncBucket = prefs.syncBucket,
                        syncRegion = prefs.syncRegion,
                        syncDirectory = prefs.syncDirectory,
                        syncDisplayName = prefs.syncDisplayName
                    )
                }
            }
        }
    }

    fun onToggleReminder(value: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(value) }
    }

    fun onReminderTimeChanged(time: String) {
        viewModelScope.launch { repository.setReminderTime(time) }
    }

    fun onToggleSavePasswordLocally(value: Boolean) {
        viewModelScope.launch { repository.setSavePasswordLocally(value) }
    }

    fun onToggleSync(value: Boolean) {
        viewModelScope.launch { repository.setSyncEnabled(value) }
    }

    fun onSyncProviderChanged(provider: String) {
        // 立即写库，便于切换后下一次进入页面保留选择。
        viewModelScope.launch { repository.setSyncProvider(provider) }
    }

    fun onSyncBucketChanged(value: String) {
        _uiState.update { it.copy(syncBucket = value, saveStatus = SaveStatus.Idle) }
    }

    fun onSyncRegionChanged(value: String) {
        _uiState.update { it.copy(syncRegion = value, saveStatus = SaveStatus.Idle) }
    }

    fun onSyncDirectoryChanged(value: String) {
        _uiState.update { it.copy(syncDirectory = value, saveStatus = SaveStatus.Idle) }
    }

    fun onSyncDisplayNameChanged(value: String) {
        _uiState.update { it.copy(syncDisplayName = value, saveStatus = SaveStatus.Idle) }
    }

    // 敏感字段仅保留在内存，不入 DataStore。
    fun onSyncAccessKeyIdChanged(value: String) {
        _uiState.update { it.copy(syncAccessKeyId = value, saveStatus = SaveStatus.Idle) }
    }

    fun onSyncAccessKeySecretChanged(value: String) {
        _uiState.update { it.copy(syncAccessKeySecret = value, saveStatus = SaveStatus.Idle) }
    }

    /**
     * 保存非敏感云同步配置；当前未实现真实联网验证，仅模拟一段延迟后置为 Saved。
     * TODO: 接入真正的 OSS/COS 连通性校验后再切换状态。
     */
    fun onSaveSyncConfiguration() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }
            repository.saveSyncConfiguration(
                provider = snapshot.syncProvider,
                bucket = snapshot.syncBucket,
                region = snapshot.syncRegion,
                directory = snapshot.syncDirectory,
                displayName = snapshot.syncDisplayName
            )
            if (saveDelayMillis > 0) {
                delay(saveDelayMillis)
            }
            _uiState.update { it.copy(saveStatus = SaveStatus.Saved) }
        }
    }

    companion object {
        private const val DEFAULT_SAVE_DELAY_MILLIS: Long = 400L

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    return SettingsViewModel(application.userPreferencesRepository) as T
                }
            }
    }
}
