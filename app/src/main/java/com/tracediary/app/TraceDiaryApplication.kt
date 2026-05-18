package com.tracediary.app

import android.app.Application
import androidx.room.Room
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.notifications.DailyReminderWorker
import com.tracediary.app.data.repository.DefaultAnnualSummaryRepository
import com.tracediary.app.data.repository.DefaultDiaryRepository
import com.tracediary.app.data.repository.DefaultSyncMetadataRepository
import com.tracediary.app.data.security.PassphraseStore
import com.tracediary.app.data.security.SecureCredentialStore
import com.tracediary.app.data.settings.DataStoreUserPreferencesRepository
import com.tracediary.app.data.settings.UserPreferences
import com.tracediary.app.data.settings.UserPreferencesRepository
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
import kotlinx.coroutines.launch
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

    lateinit var secureCredentialStore: SecureCredentialStore
        private set

    lateinit var syncEngine: SyncEngine
        private set

    lateinit var syncScheduler: SyncScheduler
        private set

    val calendarUseCase = DiaryCalendarUseCase()
    val statisticsUseCase = DiaryStatisticsUseCase()
    val searchUseCase = SearchDiaryEntriesUseCase()

    // 导入导出用例：lazy 初始化，确保 onCreate 完成后 repository 已就绪。
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // SQLCipher 加密本地数据库。passphrase 通过 EncryptedSharedPreferences 安全存放，
        // 首版采用破坏式迁移（既有数据为测试数据，可丢弃）。
        // sqlcipher-android 4.6+ 通过 System.loadLibrary 加载原生库，且 .so 已 16 KB 对齐。
        System.loadLibrary("sqlcipher")
        val passphrase = PassphraseStore(this).getOrCreate()
        database = buildDatabase(passphrase)

        diaryRepository = DefaultDiaryRepository(database.diaryEntryDao())
        annualSummaryRepository = DefaultAnnualSummaryRepository(database.annualSummaryDao())
        userPreferencesRepository = DataStoreUserPreferencesRepository(applicationContext)
        dailyReminderScheduler = DailyReminderScheduler(applicationContext)

        // 同步相关：先建凭证库，再建元数据仓库，最后组装 SyncEngine。
        secureCredentialStore = SecureCredentialStore(applicationContext)
        syncMetadataRepository = DefaultSyncMetadataRepository(database.syncMetadataDao())
        syncEngine = SyncEngine(
            storageProvider = { buildRemoteStorageOrNull() },
            diaryRepository = diaryRepository,
            annualSummaryRepository = annualSummaryRepository,
            syncMetadataRepository = syncMetadataRepository,
            clock = Clock.systemDefaultZone(),
        )

        // 持续缓存 preferences 最新值，供 storageProvider 同步读取。
        applicationScope.launch {
            userPreferencesRepository.preferences.collect { latestPreferences = it }
        }

        // 提前创建通知 channel，避免 worker 第一次执行时还没注册 channel。
        DailyReminderWorker.ensureChannel(applicationContext)

        // 周期同步调度器：仅实例化，由用户在设置页切换开关时显式 schedule / cancel。
        syncScheduler = SyncScheduler(applicationContext)
    }

    /**
     * 构建 Room 数据库。带版本标记自愈：当 SQLCipher 加密参数发生不兼容变更
     * （例如 4.5→4.6 默认 HMAC 算法切换）时，旧库无法被新版 SQLCipher 解密，
     * 进程会在首次访问 DAO 时崩溃。按"首版测试数据可丢弃"约定，发现版本不
     * 匹配时直接删库重建，避免线上闪退。
     */
    private fun buildDatabase(passphrase: ByteArray): TraceDiaryDatabase {
        val markerPrefs = getSharedPreferences(DB_META_PREFS, MODE_PRIVATE)
        if (markerPrefs.getInt(KEY_DB_FORMAT_VERSION, 0) != CURRENT_DB_FORMAT_VERSION) {
            deleteDatabase("tracediary.db")
            markerPrefs.edit().putInt(KEY_DB_FORMAT_VERSION, CURRENT_DB_FORMAT_VERSION).apply()
        }
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
        const val CURRENT_DB_FORMAT_VERSION = 1
    }
}
