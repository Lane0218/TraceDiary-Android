package com.tracediary.app

import android.app.Application
import androidx.room.Room
import com.tracediary.app.data.ai.AiModelManager
import com.tracediary.app.data.ai.AiSearchCoordinator
import com.tracediary.app.data.ai.DataStoreAiPreferencesRepository
import com.tracediary.app.data.local.SqlCipherRekey
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.notifications.DailyReminderWorker
import com.tracediary.app.data.repository.DefaultAnnualSummaryRepository
import com.tracediary.app.data.repository.DefaultDiaryRepository
import com.tracediary.app.data.repository.DefaultPendingSyncDeletionRepository
import com.tracediary.app.data.repository.DefaultSyncMetadataRepository
import com.tracediary.app.data.security.AppPasswordManager
import com.tracediary.app.data.security.CryptoBox
import com.tracediary.app.data.security.SecureCredentialStore
import com.tracediary.app.data.settings.DataStoreUserPreferencesRepository
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.settings.parseBoundaryTime
import com.tracediary.app.data.settings.resolveAppDate
import com.tracediary.app.data.sync.AliyunOssRemoteStorage
import com.tracediary.app.data.sync.RemoteCredential
import com.tracediary.app.data.sync.RemoteStorage
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.data.sync.SyncProvider
import com.tracediary.app.data.sync.SyncScheduler
import com.tracediary.app.data.sync.TencentCosRemoteStorage
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.PendingSyncDeletionRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.DiaryStatisticsUseCase
import com.tracediary.app.domain.usecase.ExportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ImportDiaryDataUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Clock

class TraceDiaryApplication : Application() {
    lateinit var database: TraceDiaryDatabase
        private set

    lateinit var diaryRepository: DiaryRepository
        private set

    lateinit var annualSummaryRepository: AnnualSummaryRepository
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    lateinit var dailyReminderScheduler: DailyReminderScheduler
        private set

    lateinit var syncMetadataRepository: SyncMetadataRepository
        private set

    lateinit var pendingSyncDeletionRepository: PendingSyncDeletionRepository
        private set

    lateinit var secureCredentialStore: SecureCredentialStore
        private set

    lateinit var appPasswordManager: AppPasswordManager
        private set

    lateinit var syncEngine: SyncEngine
        private set

    lateinit var syncScheduler: SyncScheduler
        private set

    lateinit var aiSearchCoordinator: AiSearchCoordinator
        private set

    private lateinit var aiPreferencesRepository: DataStoreAiPreferencesRepository

    @Volatile
    private var acceptedPassword: String? = null

    val calendarUseCase = DiaryCalendarUseCase()
    val statisticsUseCase = DiaryStatisticsUseCase()
    val searchUseCase = SearchDiaryEntriesUseCase()

    // 导入导出用例：lazy 初始化，确保解锁并初始化数据层后 repository 已就绪。
    val exportDiaryDataUseCase: ExportDiaryDataUseCase by lazy {
        ExportDiaryDataUseCase(
            diaryRepository = diaryRepository,
            annualSummaryRepository = annualSummaryRepository,
            clock = Clock.systemDefaultZone(),
        )
    }
    val importDiaryDataUseCase: ImportDiaryDataUseCase by lazy {
        ImportDiaryDataUseCase(
            diaryRepository = diaryRepository,
            annualSummaryRepository = annualSummaryRepository,
        )
    }

    /**
     * 用户偏好的最新快照。SyncEngine.storageProvider 是同步 lambda，
     * 不能直接 collect Flow，所以这里维护一个 @Volatile 缓存，由
     * applicationScope 持续 collect preferences 写入。
     */
    @Volatile
    private var latestPreferences: UserPreferences? = null

    @Volatile
    private var cachedAppDate: LocalDate? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // sqlcipher-android 4.6+ 通过 System.loadLibrary 加载原生库，且 .so 已 16 KB 对齐。
        System.loadLibrary("sqlcipher")
        userPreferencesRepository = DataStoreUserPreferencesRepository(applicationContext)
        aiPreferencesRepository = DataStoreAiPreferencesRepository(applicationContext)
        dailyReminderScheduler = DailyReminderScheduler(applicationContext)
        secureCredentialStore = SecureCredentialStore(applicationContext)
        // 迁移旧版本的周期提醒；新版本只保留 AlarmManager 单次精确闹钟。
        dailyReminderScheduler.cancelLegacyPeriodicWork()

        runBlocking(Dispatchers.IO) {
            val prefs = userPreferencesRepository.preferences.first()
            latestPreferences = prefs
            cachedAppDate = resolveAppDate(
                LocalDateTime.now(),
                parseBoundaryTime(prefs.dayBoundaryTime),
            )
        }

        // 持续缓存 preferences 最新值，供 storageProvider 同步读取。
        applicationScope.launch {
            userPreferencesRepository.preferences.collect { latestPreferences = it }
        }

        // 提前创建通知 channel，避免 worker 第一次执行时还没注册 channel。
        DailyReminderWorker.ensureChannel(applicationContext)

        // 周期同步调度器：仅实例化，由用户在设置页切换开关时显式 schedule / cancel。
        syncScheduler = SyncScheduler(applicationContext)

        appPasswordManager = AppPasswordManager(
            context = applicationContext,
            credentialStore = secureCredentialStore,
            onPasswordAccepted = { password ->
                acceptedPassword = password
                try {
                    initializeDataLayer(password)
                } catch (error: Throwable) {
                    acceptedPassword = null
                    throw error
                }
                applicationScope.launch(Dispatchers.IO) {
                    if (::aiSearchCoordinator.isInitialized) aiSearchCoordinator.unlock()
                }
            },
            onLocked = {
                acceptedPassword = null
                applicationScope.launch(Dispatchers.IO) {
                    if (::aiSearchCoordinator.isInitialized) aiSearchCoordinator.lock()
                }
            },
        )

        // 冷启动、设备重启或系统回收进程后，根据持久化设置恢复下一次精确闹钟。
        applicationScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.preferences.first()
            if (!prefs.dailyReminderEnabled) return@launch
            val readyForBackgroundReminder =
                appPasswordManager.isRememberPasswordEnabled() &&
                    dailyReminderScheduler.canScheduleExactAlarms()
            if (!readyForBackgroundReminder) {
                userPreferencesRepository.setReminderEnabled(false)
                dailyReminderScheduler.cancel()
                return@launch
            }
            try {
                dailyReminderScheduler.scheduleForRecovery(
                    timeOfDay = DailyReminderScheduler.parseTime(prefs.dailyReminderTime),
                    boundaryTime = parseBoundaryTime(prefs.dayBoundaryTime),
                )
            } catch (_: Throwable) {
                userPreferencesRepository.setReminderEnabled(false)
                dailyReminderScheduler.cancel()
            }
        }
    }

    @Synchronized
    fun isDataLayerReady(): Boolean = ::database.isInitialized

    @Synchronized
    fun initializeDataLayer(password: String) {
        if (::database.isInitialized) return
        val passphrase = CryptoBox.deriveDatabasePassphrase(password)
        val openedDatabase = buildDatabase(passphrase)
        try {
            // 强制执行 SQLCipher 打开与口令校验。恢复数据库后若密码错误，必须在写入
            // 新安装的密码验证器之前失败，不能先进入 UI 再在首次 DAO 查询时崩溃。
            openedDatabase.openHelper.writableDatabase
        } catch (error: Throwable) {
            openedDatabase.close()
            throw error
        }
        database = openedDatabase

        diaryRepository = DefaultDiaryRepository(database.diaryEntryDao())
        annualSummaryRepository = DefaultAnnualSummaryRepository(database.annualSummaryDao())
        syncMetadataRepository = DefaultSyncMetadataRepository(database.syncMetadataDao())
        pendingSyncDeletionRepository =
            DefaultPendingSyncDeletionRepository(database.pendingSyncDeletionDao())
        syncEngine = SyncEngine(
            storageProvider = { buildRemoteStorageOrNull() },
            diaryRepository = diaryRepository,
            annualSummaryRepository = annualSummaryRepository,
            syncMetadataRepository = syncMetadataRepository,
            pendingSyncDeletionRepository = pendingSyncDeletionRepository,
            passwordProvider = { appPasswordManager.loadActivePassword() },
            clock = Clock.systemDefaultZone(),
        )
        aiSearchCoordinator = AiSearchCoordinator(
            context = applicationContext,
            diaryRepository = diaryRepository,
            annualSummaryRepository = annualSummaryRepository,
            userPreferencesRepository = userPreferencesRepository,
            aiPreferencesRepository = aiPreferencesRepository,
            credentialStore = secureCredentialStore,
            passwordProvider = {
                acceptedPassword ?: if (::appPasswordManager.isInitialized) {
                    appPasswordManager.loadActivePassword()
                } else null
            },
            unlockedProvider = {
                acceptedPassword != null ||
                    (::appPasswordManager.isInitialized && appPasswordManager.isUnlocked())
            },
            currentDateProvider = ::currentAppDate,
            scope = applicationScope,
            modelManager = AiModelManager(applicationContext),
            statisticsUseCase = statisticsUseCase,
        )
    }

    @Synchronized
    fun rekeyDatabase(newPassword: String) {
        check(::database.isInitialized) { "数据库尚未初始化" }
        if (::aiSearchCoordinator.isInitialized) {
            runBlocking(Dispatchers.IO) { aiSearchCoordinator.onPasswordWillChange() }
        }
        val newPassphrase = CryptoBox.deriveDatabasePassphrase(newPassword)
        SqlCipherRekey.rekey(database.openHelper.writableDatabase, newPassphrase)
        acceptedPassword = newPassword
    }

    fun resumeAiAfterPasswordChange() {
        if (::aiSearchCoordinator.isInitialized) {
            applicationScope.launch(Dispatchers.IO) { aiSearchCoordinator.unlock() }
        }
    }

    fun resolveCurrentAppDate(now: LocalDateTime = LocalDateTime.now()): LocalDate {
        val prefs = latestPreferences
        return if (prefs != null) {
            resolveAppDate(now, parseBoundaryTime(prefs.dayBoundaryTime))
        } else {
            resolveAppDate(now, parseBoundaryTime("00:00"))
        }
    }

    fun currentAppDate(): LocalDate {
        return cachedAppDate ?: resolveCurrentAppDate()
    }

    /**
     * 构建 Room 数据库。带版本标记自愈：当 SQLCipher 加密参数发生不兼容变更
     * （例如 4.5→4.6 默认 HMAC 算法切换）时，旧库无法被新版 SQLCipher 解密，
     * 进程会在首次访问 DAO 时崩溃。按"首版测试数据可丢弃"约定，发现版本不
     * 匹配时直接删库重建，避免线上闪退。
     */
    private fun buildDatabase(passphrase: ByteArray): TraceDiaryDatabase {
        val markerPrefs = getSharedPreferences(DB_META_PREFS, MODE_PRIVATE)
        val storedFormatVersion = markerPrefs.getInt(KEY_DB_FORMAT_VERSION, 0)
        if (shouldDeleteDatabaseForFormat(storedFormatVersion, CURRENT_DB_FORMAT_VERSION)) {
            deleteDatabase("tracediary.db")
        }
        // 标记缺失可能是重新安装后恢复了数据库文件，不能据此删库。固定口令派生
        // 允许我们直接尝试打开；只有明确记录了不兼容旧格式时才按首版策略清理。
        markerPrefs.edit().putInt(KEY_DB_FORMAT_VERSION, CURRENT_DB_FORMAT_VERSION).apply()
        return Room.databaseBuilder(
            this,
            TraceDiaryDatabase::class.java,
            "tracediary.db"
        )
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .fallbackToDestructiveMigration(true)
            .build()
    }

    /**
     * 根据当前偏好与凭证拼装 RemoteStorage。任一环节缺配置就返回 null，
     * 由 SyncEngine 转为 [com.tracediary.app.data.sync.ErrorCode.AUTH] 错误。
     */
    private fun buildRemoteStorageOrNull(): RemoteStorage? {
        val prefs = latestPreferences ?: return null
        if (!prefs.syncEnabled) return null
        if (prefs.syncBucket.isBlank() || prefs.syncRegion.isBlank()) return null
        val (id, secret) = secureCredentialStore.loadCloudCredential() ?: return null
        val provider = when (prefs.syncProvider.lowercase()) {
            "aliyun", "oss", "aliyun_oss" -> SyncProvider.ALIYUN_OSS
            "tencent", "cos", "tencent_cos" -> SyncProvider.TENCENT_COS
            else -> return null
        }
        val credential = RemoteCredential(
            provider = provider,
            region = prefs.syncRegion,
            bucket = prefs.syncBucket,
            accessKeyId = id,
            accessKeySecret = secret,
            pathPrefix = prefs.syncDirectory.trim('/'),
        )
        return when (provider) {
            SyncProvider.ALIYUN_OSS -> AliyunOssRemoteStorage(credential)
            SyncProvider.TENCENT_COS -> TencentCosRemoteStorage(credential)
        }
    }

    private companion object {
        const val DB_META_PREFS = "tracediary_db_meta"
        const val KEY_DB_FORMAT_VERSION = "db_format_version"
        // 升级 SQLCipher 大版本或修改加密参数时 +1，触发启动时清库重建。
        // v3：SQLCipher passphrase 改为由应用密码固定派生，不再使用本机随机 passphrase。
        const val CURRENT_DB_FORMAT_VERSION = 3
    }
}

internal fun shouldDeleteDatabaseForFormat(storedVersion: Int, currentVersion: Int): Boolean =
    storedVersion != 0 && storedVersion != currentVersion
