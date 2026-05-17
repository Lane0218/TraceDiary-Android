package com.tracediary.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.domain.usecase.ExportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ImportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ImportOptions
import com.tracediary.app.domain.usecase.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
 * 数据备份/恢复的状态机：用于导入导出按钮和弹窗反馈。
 */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data class Success(val message: String) : BackupStatus
    data class Failed(val message: String) : BackupStatus
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
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val backupStatus: BackupStatus = BackupStatus.Idle,
    val lastImportResult: ImportResult? = null,
)

class SettingsViewModel(
    private val repository: UserPreferencesRepository,
    private val reminderScheduler: DailyReminderScheduler? = null,
    private val exportUseCase: ExportDiaryDataUseCase? = null,
    private val importUseCase: ImportDiaryDataUseCase? = null,
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
        viewModelScope.launch {
            repository.setReminderEnabled(value)
            // 写完 DataStore 之后再调度，确保 UI 与后台任务一致。
            if (value) {
                runCatching {
                    val time = DailyReminderScheduler.parseTime(_uiState.value.dailyReminderTime)
                    reminderScheduler?.schedule(time)
                }
            } else {
                reminderScheduler?.cancel()
            }
        }
    }

    fun onReminderTimeChanged(time: String) {
        viewModelScope.launch {
            repository.setReminderTime(time)
            // 用户改时间后，如果当前是开启状态需要立即重新调度。
            if (_uiState.value.dailyReminderEnabled) {
                runCatching {
                    reminderScheduler?.schedule(DailyReminderScheduler.parseTime(time))
                }
            }
        }
    }

    /**
     * 通知权限对话框的回调：
     * - granted=false 且当前提醒已开启时，把开关回滚成 false 并取消已调度的任务，
     *   避免用户拒绝权限后仍误以为提醒生效。
     */
    fun onNotificationPermissionGranted(granted: Boolean) {
        if (granted) return
        if (!_uiState.value.dailyReminderEnabled) return
        viewModelScope.launch {
            repository.setReminderEnabled(false)
            reminderScheduler?.cancel()
        }
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

    /**
     * 导出当前数据库内容到 SAF 选定的 Uri。
     *
     * - JSON 序列化与文件 I/O 均放在 [Dispatchers.IO]，避免阻塞 UI 线程。
     * - 失败时使用中文兜底消息，避免把底层异常字符串直接抛给用户。
     */
    fun onExport(uri: Uri, contentResolver: ContentResolver) {
        val useCase = exportUseCase ?: run {
            _uiState.update { it.copy(backupStatus = BackupStatus.Failed("导出功能尚未初始化")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val payload = withContext(Dispatchers.IO) { useCase() }
                val entryCount = payload.estimatedEntries()
                val summaryCount = payload.estimatedSummaries()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        ?: error("无法写入所选位置")
                }
                val message = buildString {
                    append("已导出 ")
                    append(entryCount)
                    append(" 条日记")
                    if (summaryCount > 0) {
                        append("、")
                        append(summaryCount)
                        append(" 篇年度总结")
                    }
                }
                _uiState.update { it.copy(backupStatus = BackupStatus.Success(message)) }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(backupStatus = BackupStatus.Failed("导出失败：${t.message ?: "未知错误"}"))
                }
            }
        }
    }

    /**
     * 从 SAF 选定的 Uri 读取备份 JSON 并按 [options] 合并入库。
     */
    fun onImport(uri: Uri, contentResolver: ContentResolver, options: ImportOptions) {
        val useCase = importUseCase ?: run {
            _uiState.update { it.copy(backupStatus = BackupStatus.Failed("导入功能尚未初始化")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val payload = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取所选文件")
                }
                val result = useCase(payload, options)
                val message = buildString {
                    append("导入 ")
                    append(result.importedEntries)
                    append(" 条日记")
                    if (result.skippedEntries > 0) {
                        append("（跳过 ")
                        append(result.skippedEntries)
                        append(" 条）")
                    }
                    if (result.importedSummaries > 0 || result.skippedSummaries > 0) {
                        append("、")
                        append(result.importedSummaries)
                        append(" 篇年度总结")
                        if (result.skippedSummaries > 0) {
                            append("（跳过 ")
                            append(result.skippedSummaries)
                            append(" 篇）")
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        backupStatus = BackupStatus.Success(message),
                        lastImportResult = result,
                    )
                }
            } catch (t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() } ?: "未知错误"
                _uiState.update {
                    it.copy(backupStatus = BackupStatus.Failed("导入失败：$message"))
                }
            }
        }
    }

    /** 用于关闭导入导出反馈弹窗后清理状态。 */
    fun resetBackupStatus() {
        _uiState.update { it.copy(backupStatus = BackupStatus.Idle) }
    }

    // 通过对 JSON 文本中字段名的简单计数估算条目数量，避免在 onExport 中重复反序列化。
    private fun String.estimatedEntries(): Int = countOccurrences("\"date\"")
    private fun String.estimatedSummaries(): Int = countOccurrences("\"year\"")

    private fun String.countOccurrences(token: String): Int {
        if (token.isEmpty()) return 0
        var count = 0
        var index = indexOf(token)
        while (index >= 0) {
            count++
            index = indexOf(token, index + token.length)
        }
        return count
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
                    return SettingsViewModel(
                        repository = application.userPreferencesRepository,
                        reminderScheduler = application.dailyReminderScheduler,
                        exportUseCase = application.exportDiaryDataUseCase,
                        importUseCase = application.importDiaryDataUseCase,
                    ) as T
                }
            }
    }
}
