package com.tracediary.app

import android.app.Application
import androidx.room.Room
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.data.repository.DefaultAnnualSummaryRepository
import com.tracediary.app.data.repository.DefaultDiaryRepository
import com.tracediary.app.data.settings.DataStoreUserPreferencesRepository
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.DiaryStatisticsUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase

class TraceDiaryApplication : Application() {
    lateinit var database: TraceDiaryDatabase
        private set

    lateinit var diaryRepository: DiaryRepository
        private set

    lateinit var annualSummaryRepository: AnnualSummaryRepository
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    val calendarUseCase = DiaryCalendarUseCase()
    val statisticsUseCase = DiaryStatisticsUseCase()
    val searchUseCase = SearchDiaryEntriesUseCase()

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
    }
}
