package com.tracediary.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.security.AppPasswordManager
import com.tracediary.app.data.security.SecureCredentialStore
import com.tracediary.app.data.settings.CalendarWeekStart
import com.tracediary.app.data.settings.ThemeColor
import com.tracediary.app.data.settings.formatBoundaryTime
import com.tracediary.app.data.settings.SameDayEntrySortOrder
import com.tracediary.app.data.settings.parseBoundaryTime
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.sync.AliyunOssRemoteStorage
import com.tracediary.app.data.sync.RemoteCredential
import com.tracediary.app.data.sync.RemoteResult
import com.tracediary.app.data.sync.RemoteStorage
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.data.sync.SyncProvider
import com.tracediary.app.data.sync.SyncWorkScheduler
import com.tracediary.app.data.sync.TencentCosRemoteStorage
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.ExportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ExportFormat
import com.tracediary.app.domain.usecase.ImportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ImportOptions
import com.tracediary.app.domain.usecase.ImportResult
import com.tracediary.app.domain.usecase.MergeStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

enum class ReminderBlocker {
    RememberPassword,
    ExactAlarmPermission,
}

/**
 * 设置页 UI 状态。包含 DataStore 持久化字段以及仅在内存中保留的敏感字段。
 */
data class SettingsUiState(
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "21:00",
    val dayBoundaryTime: String = "00:00",
    val reminderError: String? = null,
    val reminderBlocker: ReminderBlocker? = null,
    val savePasswordLocally: Boolean = true,
    val syncEnabled: Boolean = false,
    val syncProvider: String = "aliyun",
    val syncDisplayName: String = "",
    val syncBucket: String = "",
    val syncRegion: String = "",
    val syncDirectory: String = "TraceDiary/",
    val sameDayEntrySortOrder: SameDayEntrySortOrder = SameDayEntrySortOrder.LatestFirst,
    val calendarWeekStart: CalendarWeekStart = CalendarWeekStart.Sunday,
    val themeColor: ThemeColor = ThemeColor.Forest,
    // AK/SK 与“记住密码”缓存：通过 SecureCredentialStore（EncryptedSharedPreferences）持久化。
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
    val manualSyncStrategy: MergeStrategy = MergeStrategy.KeepNewer,
    val syncOpStatus: SyncOpStatus = SyncOpStatus.Idle,
    val passwordRotationStatus: PasswordRotationStatus = PasswordRotationStatus.Idle,
    val lastSyncText: String = "暂无同步记录",
)

private data class SyncConfigurationDraft(
    val displayName: String,
    val bucket: String,
    val region: String,
    val directory: String,
    val accessKeyId: String,
    val accessKeySecret: String,
) {
    companion object {
        val Empty = SyncConfigurationDraft(
            displayName = "",
            bucket = "",
            region = "",
            directory = "TraceDiary/",
            accessKeyId = "",
            accessKeySecret = "",
        )
    }
}

class SettingsViewModel(
    private val repository: UserPreferencesRepository,
    private val reminderScheduler: DailyReminderScheduler? = null,
    private val exportUseCase: ExportDiaryDataUseCase? = null,
    private val importUseCase: ImportDiaryDataUseCase? = null,
    private val saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS,
    private val secureCredentialStore: SecureCredentialStore? = null,
    private val syncEngine: SyncEngine? = null,
    private val syncScheduler: SyncWorkScheduler? = null,
    private val appPasswordManager: AppPasswordManager? = null,
    private val syncMetadataRepository: SyncMetadataRepository? = null,
    private val remoteStorageFactory: ((SettingsUiState) -> RemoteStorage?)? = null,
    private val databaseRekeyer: (suspend (String) -> Unit)? = null,
    private val passwordRotationCompleter: (suspend () -> Unit)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zoneId),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val syncDraftsByProvider = mutableMapOf<String, SyncConfigurationDraft>()

    /** 暴露同步引擎进度供 UI 直接 collect，避免在 VM 中重复维护一份镜像。 */
    val syncEngineState: StateFlow<SyncEngine.SyncEngineState>? = syncEngine?.state
    private var saveJob: Job? = null

    init {
        // 启动时把已持久化的 AK/SK 与本机密码缓存回填到内存 state，保证页面打开就能看到。
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
        // 收集 DataStore 偏好，与 UI state 保持同步；未验证的同步字段保留在当前草稿。
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update { current ->
                    val keepDraft = syncDraftsByProvider.containsKey(current.syncProvider)
                    current.copy(
                    dailyReminderEnabled = prefs.dailyReminderEnabled,
                    dailyReminderTime = prefs.dailyReminderTime,
                    dayBoundaryTime = formatBoundaryTime(parseBoundaryTime(prefs.dayBoundaryTime)),
                    savePasswordLocally = prefs.savePasswordLocally,
                        syncEnabled = prefs.syncEnabled,
                        syncProvider = if (keepDraft) current.syncProvider else prefs.syncProvider,
                        syncDisplayName = if (keepDraft) current.syncDisplayName else prefs.syncDisplayName,
                        syncBucket = if (keepDraft) current.syncBucket else prefs.syncBucket,
                        syncRegion = if (keepDraft) current.syncRegion else prefs.syncRegion,
                        syncDirectory = if (keepDraft) current.syncDirectory else prefs.syncDirectory,
                        sameDayEntrySortOrder = prefs.sameDayEntrySortOrder,
                        calendarWeekStart = prefs.calendarWeekStart,
                        themeColor = prefs.themeColor,
                    )
                }
            }
        }
        viewModelScope.launch {
            syncMetadataRepository?.observeLatestLastSyncedAt()?.collect { lastSyncedAt ->
                _uiState.update { current ->
                    current.copy(lastSyncText = formatLastSyncText(lastSyncedAt, zoneId, clock))
                }
            }
        }
    }

    fun onToggleReminder(value: Boolean) {
        if (value && appPasswordManager?.isRememberPasswordEnabled() == false) {
            _uiState.update {
                it.copy(
                    reminderError = "开启提醒前请先开启“本机记住密码”",
                    reminderBlocker = ReminderBlocker.RememberPassword,
                )
            }
            return
        }
        if (value && reminderScheduler?.canScheduleExactAlarms() == false) {
            _uiState.update {
                it.copy(
                    reminderError = DailyReminderScheduler.EXACT_ALARM_PERMISSION_REQUIRED,
                    reminderBlocker = ReminderBlocker.ExactAlarmPermission,
                )
            }
            return
        }
        viewModelScope.launch {
            if (value) {
                val result = runCatching {
                    val time = DailyReminderScheduler.parseTime(_uiState.value.dailyReminderTime)
                    val boundary = parseBoundaryTime(_uiState.value.dayBoundaryTime)
                    reminderScheduler?.schedule(time, boundary)
                }
                if (result.isFailure) {
                    repository.setReminderEnabled(false)
                    _uiState.update {
                        it.copy(
                            dailyReminderEnabled = false,
                            reminderError = result.exceptionOrNull()?.message ?: "提醒调度失败",
                            reminderBlocker = null,
                        )
                    }
                    return@launch
                }
            } else {
                reminderScheduler?.cancel()
            }
            repository.setReminderEnabled(value)
            _uiState.update { it.copy(reminderError = null, reminderBlocker = null) }
        }
    }

    fun onReminderTimeChanged(time: String) {
        viewModelScope.launch {
            repository.setReminderTime(time)
            // 用户改时间后，如果当前是开启状态需要立即重新调度。
            if (_uiState.value.dailyReminderEnabled) {
                runCatching {
                    reminderScheduler?.schedule(
                        timeOfDay = DailyReminderScheduler.parseTime(time),
                        boundaryTime = parseBoundaryTime(_uiState.value.dayBoundaryTime),
                    )
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(reminderError = error.message ?: "提醒调度失败")
                    }
                }
            }
        }
    }

    fun onDayBoundaryTimeChanged(time: String) {
        viewModelScope.launch {
            val normalized = formatBoundaryTime(parseBoundaryTime(time))
            repository.setDayBoundaryTime(normalized)
            if (_uiState.value.dailyReminderEnabled) {
                runCatching {
                    reminderScheduler?.schedule(
                        timeOfDay = DailyReminderScheduler.parseTime(_uiState.value.dailyReminderTime),
                        boundaryTime = parseBoundaryTime(normalized),
                    )
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(reminderError = error.message ?: "提醒调度失败")
                    }
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
        viewModelScope.launch {
            if (_uiState.value.dailyReminderEnabled) {
                repository.setReminderEnabled(false)
                reminderScheduler?.cancel()
            }
            _uiState.update {
                it.copy(
                    reminderError = "请允许通知权限后才能使用日记提醒",
                    reminderBlocker = null,
                )
            }
        }
    }

    fun canScheduleExactAlarms(): Boolean = reminderScheduler?.canScheduleExactAlarms() ?: true

    /** 从系统精确闹钟设置页返回后，若授权成功则继续完成开启流程。 */
    fun onExactAlarmPermissionResumed() {
        if (_uiState.value.reminderBlocker != ReminderBlocker.ExactAlarmPermission) return
        if (canScheduleExactAlarms()) {
            onToggleReminder(true)
        }
    }

    /**
     * 切换"在本机记住密码"开关。
     *
     * 关闭时把内存里的输入与 SecureCredentialStore 内的密码缓存一并抹除，避免开关
     * 关闭后磁盘里仍残留密文；同时同步到 [AppPasswordManager]，下一次冷启动会强制要求输入密码。
     */
    fun onToggleSavePasswordLocally(value: Boolean) {
        viewModelScope.launch {
            repository.setSavePasswordLocally(value)
            appPasswordManager?.setRememberPasswordEnabled(value)
            if (!value) {
                if (_uiState.value.dailyReminderEnabled) {
                    repository.setReminderEnabled(false)
                    reminderScheduler?.cancel()
                }
                secureCredentialStore?.saveLocalPassword(null)
                _uiState.update {
                    it.copy(
                        localPassword = "",
                        dailyReminderEnabled = false,
                        reminderError = "关闭“本机记住密码”后，日记提醒已关闭",
                        reminderBlocker = null,
                    )
                }
            }
        }
    }

    /**
     * @deprecated 应用密码迁移后改由 [AppPasswordManager] 持有；保留此字段仅用于向后兼容
     * （密码轮换成功后回填、单元测试 fixture）。新增代码应使用 [AppPasswordManager.loadActivePassword]。
     */
    fun onLocalPasswordChanged(value: String) {
        _uiState.update { it.copy(localPassword = value) }
    }

    /**
     * @deprecated 同上。仅在测试场景与历史路径中调用；新代码请使用 [AppPasswordManager.changePassword]。
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
        if (hasSyncWorkInFlight()) return
        val snapshot = _uiState.value
        val oldPassword = snapshot.oldSyncPassword
        val newPassword = snapshot.newSyncPassword
        val confirmPassword = snapshot.confirmNewSyncPassword
        when {
            oldPassword.isBlank() -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("请输入旧密码"))
                }
                return
            }
            newPassword.isBlank() -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("请输入新密码"))
                }
                return
            }
            newPassword != confirmPassword -> {
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("两次输入的新密码不一致"))
                }
                return
            }
        }

        val manager = appPasswordManager
        if (manager != null && !manager.verifyPassword(oldPassword)) {
            _uiState.update {
                it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("旧密码错误"))
            }
            return
        }

        _uiState.update { it.copy(passwordRotationStatus = PasswordRotationStatus.Working) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { databaseRekeyer?.invoke(newPassword) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("本地数据库更新失败：${t.message ?: "未知错误"}"))
                }
                return@launch
            }

            val engine = syncEngine
            val remoteResult: RemoteResult<SyncEngine.SyncSummary>? = if (engine != null) {
                try {
                    withContext(ioDispatcher) { engine.rotateSyncPassword(oldPassword, newPassword) }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    RemoteResult.Err(
                        code = com.tracediary.app.data.sync.ErrorCode.UNKNOWN,
                        message = "${t.javaClass.simpleName}: ${t.message ?: "未知异常"}",
                        cause = t,
                    )
                }
            } else null

            // 远端未配置时继续本地轮换；其它错误回滚本地 SQLCipher passphrase。
            val remoteOk = when (remoteResult) {
                null -> true
                is RemoteResult.Ok -> true
                is RemoteResult.Err -> remoteResult.code == com.tracediary.app.data.sync.ErrorCode.AUTH &&
                    remoteResult.message == "未配置云同步"
            }
            if (!remoteOk) {
                runCatching { withContext(ioDispatcher) { databaseRekeyer?.invoke(oldPassword) } }
                val err = remoteResult as RemoteResult.Err
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed(err.message))
                }
                return@launch
            }

            val localOk = manager?.changePassword(oldPassword, newPassword)?.isSuccess ?: true
            if (!localOk) {
                runCatching { withContext(ioDispatcher) { databaseRekeyer?.invoke(oldPassword) } }
                _uiState.update {
                    it.copy(passwordRotationStatus = PasswordRotationStatus.Failed("本地密码更新失败"))
                }
                return@launch
            }

            withContext(ioDispatcher) { passwordRotationCompleter?.invoke() }

            _uiState.update {
                it.copy(
                    localPassword = newPassword,
                    oldSyncPassword = "",
                    newSyncPassword = "",
                    confirmNewSyncPassword = "",
                    passwordRotationStatus = PasswordRotationStatus.Success("应用密码已更新"),
                )
            }
        }
    }

    fun onToggleSync(value: Boolean) {
        viewModelScope.launch {
            repository.setSyncEnabled(value)
            // 新版本不再启用周期同步；关闭时仍取消旧版本可能已经入队的周期任务。
            if (!value) {
                syncScheduler?.cancel()
            }
        }
    }

    fun onSyncProviderChanged(provider: String) {
        val current = _uiState.value
        if (provider == current.syncProvider) return
        syncDraftsByProvider[current.syncProvider] = current.toSyncConfigurationDraft()
        val restoredDraft = syncDraftsByProvider[provider] ?: SyncConfigurationDraft.Empty
        syncDraftsByProvider[provider] = restoredDraft
        _uiState.update {
            it.copy(
                syncProvider = provider,
                syncDisplayName = restoredDraft.displayName,
                syncBucket = restoredDraft.bucket,
                syncRegion = restoredDraft.region,
                syncDirectory = restoredDraft.directory,
                syncAccessKeyId = restoredDraft.accessKeyId,
                syncAccessKeySecret = restoredDraft.accessKeySecret,
                saveStatus = SaveStatus.Idle,
                saveError = null,
            )
        }
    }

    fun onSyncDisplayNameChanged(value: String) {
        updateSyncField { it.copy(syncDisplayName = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncBucketChanged(value: String) {
        updateSyncField { it.copy(syncBucket = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncRegionChanged(value: String) {
        updateSyncField { it.copy(syncRegion = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncDirectoryChanged(value: String) {
        updateSyncField { it.copy(syncDirectory = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSameDayEntrySortOrderChanged(value: SameDayEntrySortOrder) {
        _uiState.update { it.copy(sameDayEntrySortOrder = value) }
        viewModelScope.launch { repository.setSameDayEntrySortOrder(value) }
    }

    fun onCalendarWeekStartChanged(value: CalendarWeekStart) {
        _uiState.update { it.copy(calendarWeekStart = value) }
        viewModelScope.launch { repository.setCalendarWeekStart(value) }
    }

    fun onThemeColorChanged(value: ThemeColor) {
        if (_uiState.value.themeColor == value) return
        _uiState.update { it.copy(themeColor = value) }
        viewModelScope.launch { repository.setThemeColor(value) }
    }

    /** AK/SK 只更新当前草稿，验证成功后才写入加密凭证存储。 */
    fun onSyncAccessKeyIdChanged(value: String) {
        updateSyncField { it.copy(syncAccessKeyId = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun onSyncAccessKeySecretChanged(value: String) {
        updateSyncField { it.copy(syncAccessKeySecret = value, saveStatus = SaveStatus.Idle, saveError = null) }
    }

    private fun updateSyncField(transform: (SettingsUiState) -> SettingsUiState) {
        val current = _uiState.value
        val next = transform(current)
        syncDraftsByProvider[next.syncProvider] = next.toSyncConfigurationDraft()
        _uiState.value = next
    }

    private fun persistCloudCredential(snapshot: SettingsUiState) {
        val store = secureCredentialStore ?: return
        val id = snapshot.syncAccessKeyId
        val secret = snapshot.syncAccessKeySecret
        if (id.isNotBlank() && secret.isNotBlank()) {
            store.saveCloudCredential(id, secret)
        } else {
            store.clearCloudCredential()
        }
    }

    /**
     * 先验证当前草稿的云同步连接，成功后才一次性保存配置和凭证。
     *
     * 失败时保留草稿，不改变已生效配置；错误消息透出到 [SettingsUiState.saveError]。
     */
    fun onSaveSyncConfiguration() {
        if (saveJob?.isActive == true) return
        val snapshot = _uiState.value
        saveJob = viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.Saving, saveError = null) }
            try {
                val pingResult = withContext(ioDispatcher) { tryConnect(snapshot) }
                when (pingResult) {
                    is RemoteResult.Ok -> {
                        repository.saveSyncConfiguration(
                            provider = snapshot.syncProvider,
                            displayName = snapshot.syncDisplayName,
                            bucket = snapshot.syncBucket,
                            region = snapshot.syncRegion,
                            directory = snapshot.syncDirectory,
                        )
                        persistCloudCredential(snapshot)
                        syncDraftsByProvider.remove(snapshot.syncProvider)
                        _uiState.update { it.copy(saveStatus = SaveStatus.Saved, saveError = null) }
                    }
                    is RemoteResult.Err -> _uiState.update {
                        it.copy(saveStatus = SaveStatus.Failed, saveError = pingResult.message)
                    }
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(saveStatus = SaveStatus.Idle, saveError = null) }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(saveStatus = SaveStatus.Failed, saveError = error.message ?: "无法验证同步连接")
                }
            }
        }
    }

    fun cancelSaveVerification() {
        saveJob?.cancel()
        saveJob = null
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle, saveError = null) }
    }

    fun resetSaveStatus() {
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle, saveError = null) }
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
        val storage = remoteStorageFactory?.invoke(snapshot) ?: buildRemoteStorage(snapshot)
        if (storage == null) {
            return RemoteResult.Err(
                code = com.tracediary.app.data.sync.ErrorCode.UNKNOWN,
                message = "未知的云服务商：${snapshot.syncProvider}",
            )
        }
        return storage.ping()
    }

    private fun buildRemoteStorage(snapshot: SettingsUiState): RemoteStorage? {
        val provider = when (snapshot.syncProvider.lowercase()) {
            "aliyun", "oss", "aliyun_oss" -> SyncProvider.ALIYUN_OSS
            "tencent", "cos", "tencent_cos" -> SyncProvider.TENCENT_COS
            else -> return null
        }
        val credential = RemoteCredential(
            provider = provider,
            region = snapshot.syncRegion,
            bucket = snapshot.syncBucket,
            accessKeyId = snapshot.syncAccessKeyId,
            accessKeySecret = snapshot.syncAccessKeySecret,
            pathPrefix = snapshot.syncDirectory.trim('/'),
        )
        return when (provider) {
            SyncProvider.ALIYUN_OSS -> AliyunOssRemoteStorage(credential)
            SyncProvider.TENCENT_COS -> TencentCosRemoteStorage(credential)
        }
    }

    // ---------- 三个一次性同步入口 ----------

    fun onManualSyncStrategyChanged(strategy: MergeStrategy) {
        _uiState.update { it.copy(manualSyncStrategy = strategy) }
    }

    fun onUploadLocal() = launchSyncOp(SyncOperation.Upload) { it.uploadAll() }
    fun onDownloadRemote() = launchSyncOp(SyncOperation.Download) { it.downloadAll() }
    fun onMergeAll() {
        val strategy = _uiState.value.manualSyncStrategy
        launchSyncOp(SyncOperation.Merge) { it.mergeAll(strategy) }
    }

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
                withContext(ioDispatcher) { action(engine) }
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
     * - 序列化 / 打包与文件 I/O 均放在 [Dispatchers.IO]，避免阻塞 UI 线程。
     * - 失败时使用中文兜底消息，避免把底层异常字符串直接抛给用户。
     */
    fun onExport(uri: Uri, contentResolver: ContentResolver, format: ExportFormat = ExportFormat.Json) {
        val useCase = exportUseCase ?: run {
            _uiState.update { it.copy(backupStatus = BackupStatus.Failed("导出功能尚未初始化")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val payload = withContext(ioDispatcher) { useCase(format) }
                withContext(ioDispatcher) {
                    contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(payload.bytes) }
                        ?: error("无法写入所选位置")
                }
                val message = buildString {
                    append("已导出 ")
                    append(payload.entryCount)
                    append(" 条日记")
                    if (payload.summaryCount > 0) {
                        append("、")
                        append(payload.summaryCount)
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
     * 从 SAF 选定的 Uri 读取备份文件并按 [options] 合并入库。支持 JSON / TXT ZIP / Markdown ZIP。
     */
    fun onImport(uri: Uri, contentResolver: ContentResolver, options: ImportOptions) {
        val useCase = importUseCase ?: run {
            _uiState.update { it.copy(backupStatus = BackupStatus.Failed("导入功能尚未初始化")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = BackupStatus.Working) }
            try {
                val payload = withContext(ioDispatcher) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    } ?: error("无法读取所选文件")
                }
                val result = withContext(ioDispatcher) { useCase(payload, options) }
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

    private fun hasSyncWorkInFlight(): Boolean {
        val state = _uiState.value
        return state.syncOpStatus is SyncOpStatus.Working ||
            state.passwordRotationStatus is PasswordRotationStatus.Working
    }

    private fun SettingsUiState.toSyncConfigurationDraft(): SyncConfigurationDraft =
        SyncConfigurationDraft(
            displayName = syncDisplayName,
            bucket = syncBucket,
            region = syncRegion,
            directory = syncDirectory,
            accessKeyId = syncAccessKeyId,
            accessKeySecret = syncAccessKeySecret,
        )

    private fun formatLastSyncText(lastSyncedAt: Instant?, zoneId: ZoneId, clock: Clock): String {
        lastSyncedAt ?: return "暂无同步记录"
        val localDateTime = lastSyncedAt.atZone(zoneId).toLocalDateTime()
        val date = localDateTime.toLocalDate()
        val today = LocalDate.now(clock.withZone(zoneId))
        val dateText = when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> MonthDayFormatter.format(localDateTime)
            else -> DateFormatter.format(localDateTime)
        }
        return "$dateText ${TimeFormatter.format(localDateTime)}"
    }

    companion object {
        private const val DEFAULT_SAVE_DELAY_MILLIS: Long = 0L
        private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
        private val MonthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
        private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
                        appPasswordManager = application.appPasswordManager,
                        syncMetadataRepository = application.syncMetadataRepository,
                        remoteStorageFactory = { snapshot -> createRemoteStorage(snapshot) },
                        databaseRekeyer = { newPassword -> application.rekeyDatabase(newPassword) },
                        passwordRotationCompleter = application::resumeAiAfterPasswordChange,
                    ) as T
                }
            }

        private fun createRemoteStorage(snapshot: SettingsUiState): RemoteStorage? {
            val provider = when (snapshot.syncProvider.lowercase()) {
                "aliyun", "oss", "aliyun_oss" -> SyncProvider.ALIYUN_OSS
                "tencent", "cos", "tencent_cos" -> SyncProvider.TENCENT_COS
                else -> return null
            }
            val credential = RemoteCredential(
                provider = provider,
                region = snapshot.syncRegion,
                bucket = snapshot.syncBucket,
                accessKeyId = snapshot.syncAccessKeyId,
                accessKeySecret = snapshot.syncAccessKeySecret,
                pathPrefix = snapshot.syncDirectory.trim('/'),
            )
            return when (provider) {
                SyncProvider.ALIYUN_OSS -> AliyunOssRemoteStorage(credential)
                SyncProvider.TENCENT_COS -> TencentCosRemoteStorage(credential)
            }
        }
    }
}
