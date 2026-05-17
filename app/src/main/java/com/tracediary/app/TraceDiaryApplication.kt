package com.tracediary.app

import android.app.Application
import androidx.room.Room
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.notifications.DailyReminderWorker
import com.tracediary.app.data.repository.DefaultAnnualSummaryRepository
import com.tracediary.app.data.repository.DefaultDiaryRepository
import com.tracediary.app.data.settings.DataStoreUserPreferencesRepository
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.DiaryStatisticsUseCase
import com.tracediary.app.domain.usecase.ExportDiaryDataUseCase
import com.tracediary.app.domain.usecase.ImportDiaryDataUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
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

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            TraceDiaryDatabase::class.java,
            "tracediary.db"
        ).build()

        diaryRepository = DefaultDiaryRepository(database.diaryEntryDao())
        annualSummaryRepository = DefaultAnnualSummaryRepository(database.annualSummaryDao())
        userPreferencesRepository = DataStoreUserPreferencesRepository(applicationContext)
        dailyReminderScheduler = DailyReminderScheduler(applicationContext)
        // 提前创建通知 channel，避免 worker 第一次执行时还没注册 channel。
        DailyReminderWorker.ensureChannel(applicationContext)
    }
}
