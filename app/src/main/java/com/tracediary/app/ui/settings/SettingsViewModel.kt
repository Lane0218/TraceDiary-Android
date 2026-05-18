package com.tracediary.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.security.SecureCredentialStore
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.sync.AliyunOssRemoteStorage
import com.tracediary.app.data.sync.RemoteCredential
import com.tracediary.app.data.sync.RemoteResult
import com.tracediary.app.data.sync.RemoteStorage
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.data.sync.SyncProvider
import com.tracediary.app.data.sync.SyncScheduler
import com.tracediary.app.data.sync.TencentCosRemoteStorage
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
 * 三个一次性同步按钮（上传 / 下载 / 合并）的执行状态。
 *
 * - [Idle]：未触发或弹窗已被用户关闭。
 * - [Working]：协程仍在跑，UI 显示进度对话框，进度数据从 [SyncEngine.state] 实时取。
 * - [Done]：成功结束，附带 [SyncEngine.SyncSummary]。
 * - [Failed]：失败，附带可显示给用户的错误消息。
 */
sealed interface SyncOpStatus {
    data object Idle : SyncOpStatus
    data class Working(val operation: SyncOperation) : SyncOpStatus
    data class Done(val operation: SyncOperation, val summary: SyncEngine.SyncSummary) : SyncOpStatus
    data class Failed(val operation: SyncOperation, val message: String) : SyncOpStatus
}

enum class SyncOperation { Upload, Download, Merge }

sealed interface PasswordRotationStatus {
    data object Idle : PasswordRotationStatus
    data object Working : PasswordRotationStatus
    data class Success(val message: String) : PasswordRotationStatus
    data class Failed(val message: String) : PasswordRotationStatus
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
    // AK/SK 与本机密码：通过 SecureCredentialStore（EncryptedSharedPreferences）持久化。
    val syncAccessKeyId: String = "",
    val syncAccessKeySecret: String = "",
    val localPassword: String = "",
    val oldSyncPassword: String = "",
    val newSyncPassword: String = "",
    val confirmNewSyncPassword: String = "",
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val saveError: String? = null,
    val backupStatus: BackupStatus = BackupStatus.Idle,
    val lastImportResult: ImportResult? = null,
    val syncOpStatus: SyncOpStatus = SyncOpStatus.Idle,
    val passwordRotationStatus: PasswordRotationStatus = PasswordRotationStatus.Idle,
)

class SettingsViewModel(
    private val repository: UserPreferencesRepository,
    private val reminderScheduler: DailyReminderScheduler? = null,
    private val exportUseCase: ExportDiaryDataUseCase? = null,
    private val importUseCase: ImportDiaryDataUseCase? = null,
    private val saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS,
    private val secureCredentialStore: SecureCredentialStore? = null,
    private val syncEngine: SyncEngine? = null,
    private val syncScheduler: SyncScheduler? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 暴露同步引擎进度供 UI 直接 collect，避免在 VM 中重复维护一份镜像。 */
    val syncEngineState: StateFlow<SyncEngine.SyncEngineState>? = syncEngine?.state

    init {
        // 启动时把已持久化的 AK/SK 与本机密码回填到内存 state，保证页面打开就能看到。
        secureCredentialStore?.let { store ->
            val cred = store.loadCloudCredential()
            val pwd = store.loadLocalPassword().orEmpty()
            _uiState.update { current ->
                current.copy(
                    syncAccessKeyId = cred?.first.orEmpty(),
                    syncAccessKeySecret = cred?.second.orEmpty(),
                    localPassword = pwd,
                )
            }
        }
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

    /**
     * 切换"在本机安全保存密码"开关。关闭时同时清除已存储的密码与内存中的输入，
     * 避免开关关闭后磁盘里仍残留密文。
     */
    fun onToggleSavePasswordLocally(value: Boolean) {
        viewModelScope.launch {
            repository.setSavePasswordLocally(value)
            if (!value) {
                secureCredentialStore?.saveLocalPassword(null)
                _uiState.update { it.copy(localPassword = "") }
            }
        }
    }

    fun onLocalPasswordChanged(value: String) {
        _uiState.update { it.copy(localPassword = value) }
    }

    /**
     * 保存本机数据密码：开关开启时持久化；开关关闭时确保磁盘清空。
     */
    fun onSaveLocalPassword() {
        val state = _uiState.value
        val store = secureCredentialStore ?: return
        if (state.savePasswordLocally) {
            store.saveLocalPassword(state.localPassword.takeIf { it.isNotEmpty() })
        } else {
            store.saveLocalPassword(null)
        }
    }

    fun onOldSyncPasswordChanged(value: String) {
        _uiState.update {
            it.copy(oldSyncPassword = value, passwordRotationStatus = PasswordRotationStatus.Idle)
        }
    }

    fun onNewSyncPasswordChanged(value: String) {
        _uiState.update {
            it.copy(newSyncPassword = value, passwordRotationStatus = PasswordRotationStatus.Idle)
        }
    }

    fun onConfirmNewSyncPasswordChanged(value: String) {
        _uiState.update {
            it.copy(confirmNewSyncPassword = value, passwordRotationStatus = PasswordRotationStatus.Idle)
        }
    }

    fun onRotateSyncPassword() {
        val engine = syncEngine ?: run {
            _uiState.update {
                it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("同步引擎尚未初始化"))
            }
            return
        }
        if (hasSyncWorkInFlight()) return
        val snapshot = _uiState.value
        val oldPassword = snapshot.oldSyncPassword
        val newPassword = snapshot.newSyncPassword
        val confirmPassword = snapshot.confirmNewSyncPassword
        when {
            oldPassword.isBlank() -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("请输入旧同步密码"))
                }
                return
            }
            newPassword.isBlank() -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("请输入新同步密码"))
                }
                return
            }
            newPassword != confirmPassword -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("两次输入的新同步密码不一致"))
                }
                return
            }
        }

        _uiState.update { it.copy(passwordRotationStatus = PasswordRotationStatus.Working) }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { engine.rotateSyncPassword(oldPassword, newPassword) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                RemoteResult.Err(
                    code = com.tracediary.app.data.sync.ErrorCode.UNKNOWN,
                    message = "${t.javaClass.simpleName}: ${t.message ?: "未知异常"}",
                    cause = t,
                )
            }
            _uiState.update {
                when (result) {
                    is RemoteResult.Ok -> it.copy(
                        localPassword = newPassword,
                        oldSyncPassword = "",
                        newSyncPassword = "",
                        confirmNewSyncPassword = "",
                        passwordRotationStatus = PasswordRotationStatus.Success("同步密码已更新"),
                    )
                    is RemoteResult.Err -> it.copy(
                        passwordRotationStatus = PasswordRotationStatus.Failed(result.message)
                    )
                }
            }
        }
    }

    fun onToggleSync(value: Boolean) {
        viewModelScope.launch {
            repository.setSyncEnabled(value)
            // 周期同步开关与 DataStore 状态保持一致：开 -> KEEP 入队，关 -> 立即取消。
            if (value) {
                syncScheduler?.schedule()
            } else {
                syncScheduler?.cancel()
            }
        }
    }

    fun onSyncProviderChanged(provider: String) {
        // 立即写库，便于切换后下一次进入页面保留选择。
        viewModelScope.launch { repository.setSyncProvider(provider) }
    }

    fun onSyncBucketChanged(value: String) {
        _uiState.update { it.copy(syncBucket = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncRegionChanged(value: String) {
        _uiState.update { it.copy(syncRegion = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncDirectoryChanged(value: String) {
        _uiState.update { it.copy(syncDirectory = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncDisplayNameChanged(value: String) {
        _uiState.update { it.copy(syncDisplayName = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    /**
     * AK 输入：先更新内存 state；当 AK/SK 同时非空时，立即落到加密 prefs；
     * 任一字段被清空时调用 [SecureCredentialStore.clearCloudCredential]，避免半截状态被保留。
     */
    fun onSyncAccessKeyIdChanged(value: String) {
        _uiState.update {
            it.copy(syncAccessKeyId = value, saveStatus = SaveStatus.Idle, saveError = null)
        }
        persistCloudCredential()
    }

    fun onSyncAccessKeySecretChanged(value: String) {
        _uiState.update {
            it.copy(syncAccessKeySecret = value, saveStatus = SaveStatus.Idle, saveError = null)
        }
        persistCloudCredential()
    }

    private fun persistCloudCredential() {
        val store = secureCredentialStore ?: return
        val state = _uiState.value
        val id = state.syncAccessKeyId
        val secret = state.syncAccessKeySecret
        if (id.isNotBlank() && secret.isNotBlank()) {
            store.saveCloudCredential(id, secret)
        } else {
            store.clearCloudCredential()
        }
    }

    /**
     * 保存非敏感云同步配置并发起一次 ping 验证连通性。
     *
     * 流程：DataStore.saveSyncConfiguration → 持久化 AK/SK → 临时 RemoteStorage.ping。
     * 失败时把错误消息透出到 [SettingsUiState.saveError]，以便 UI 展示。
     */
    fun onSaveSyncConfiguration() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.Saving, saveError = null) }
            repository.saveSyncConfiguration(
                provider = snapshot.syncProvider,
                bucket = snapshot.syncBucket,
                region = snapshot.syncRegion,
                directory = snapshot.syncDirectory,
                displayName = snapshot.syncDisplayName
            )
            // 持久化凭证（已由输入回调覆盖，但保存按钮再兜底一次，应对从 init 直接进保存的场景）。
            persistCloudCredential()

            val pingResult = withContext(Dispatchers.IO) { tryConnect(snapshot) }
            when (pingResult) {
                is RemoteResult.Ok -> _uiState.update {
                    it.copy(saveStatus = SaveStatus.Saved, saveError = null)
                }
                is RemoteResult.Err -> _uiState.update {
                    it.copy(saveStatus = SaveStatus.Failed, saveError = pingResult.message)
                }
            }
        }
    }

    /** 用当前 UI 快照构造一个临时 [RemoteStorage] 并发起一次 ping。 */
    private suspend fun tryConnect(snapshot: SettingsUiState): RemoteResult<Unit> {
        if (snapshot.syncBucket.isBlank() || snapshot.syncRegion.isBlank()) {
            return RemoteResult.Err(
                code = com.tracediary.app.data.sync.ErrorCode.AUTH,
                message = "请填写存储桶与地域",
            )
        }
        if (snapshot.syncAccessKeyId.isBlank() || snapshot.syncAccessKeySecret.isBlank()) {
            return RemoteResult.Err(
                code = com.tracediary.app.data.sync.ErrorCode.AUTH,
                message = "请填写 AccessKey ID 与 Secret",
            )
        }
        val provider = when (snapshot.syncProvider.lowercase()) {
            "aliyun", "oss", "aliyun_oss" -> SyncProvider.ALIYUN_OSS
            "tencent", "cos", "tencent_cos" -> SyncProvider.TENCENT_COS
            else -> return RemoteResult.Err(
                code = com.tracediary.app.data.sync.ErrorCode.UNKNOWN,
                message = "未知的云服务商：${snapshot.syncProvider}",
            )
        }
        val credential = RemoteCredential(
            provider = provider,
            region = snapshot.syncRegion,
            bucket = snapshot.syncBucket,
            accessKeyId = snapshot.syncAccessKeyId,
            accessKeySecret = snapshot.syncAccessKeySecret,
            pathPrefix = snapshot.syncDirectory.trim('/'),
        )
        val storage: RemoteStorage = when (provider) {
            SyncProvider.ALIYUN_OSS -> AliyunOssRemoteStorage(credential)
            SyncProvider.TENCENT_COS -> TencentCosRemoteStorage(credential)
        }
        return storage.ping()
    }

    // ---------- 三个一次性同步入口 ----------

    fun onUploadLocal() = launchSyncOp(SyncOperation.Upload) { it.uploadAll() }
    fun onDownloadRemote() = launchSyncOp(SyncOperation.Download) { it.downloadAll() }
    fun onMergeAll() = launchSyncOp(SyncOperation.Merge) { it.mergeAll() }

    private fun launchSyncOp(
        op: SyncOperation,
        action: suspend (SyncEngine) -> RemoteResult<SyncEngine.SyncSummary>,
    ) {
        val engine = syncEngine ?: run {
            _uiState.update {
                it.copy(syncOpStatus = SyncOpStatus.Failed(op, "同步引擎尚未初始化"))
            }
            return
        }
        // 若已有同步任务在跑，直接忽略，避免并发触发同一个 SyncEngine。
        if (hasSyncWorkInFlight()) return
        _uiState.update { it.copy(syncOpStatus = SyncOpStatus.Working(op)) }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { action(engine) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                RemoteResult.Err(
                    code = com.tracediary.app.data.sync.ErrorCode.UNKNOWN,
                    message = "${t.javaClass.simpleName}: ${t.message ?: "未知异常"}",
                    cause = t,
                )
            }
            _uiState.update {
                val next = when (result) {
                    is RemoteResult.Ok -> SyncOpStatus.Done(op, result.value)
                    is RemoteResult.Err -> SyncOpStatus.Failed(op, result.message)
                }
                it.copy(syncOpStatus = next)
            }
        }
    }

    fun resetSyncOpStatus() {
        _uiState.update { it.copy(syncOpStatus = SyncOpStatus.Idle) }
    }

    fun resetPasswordRotationStatus() {
        _uiState.update { it.copy(passwordRotationStatus = PasswordRotationStatus.Idle) }
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

    private fun hasSyncWorkInFlight(): Boolean {
        val state = _uiState.value
        return state.syncOpStatus is SyncOpStatus.Working ||
            state.passwordRotationStatus is PasswordRotationStatus.Working
    }

    companion object {
        private const val DEFAULT_SAVE_DELAY_MILLIS: Long = 0L

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
                        secureCredentialStore = application.secureCredentialStore,
                        syncEngine = application.syncEngine,
                        syncScheduler = application.syncScheduler,
                    ) as T
                }
            }
    }
}
