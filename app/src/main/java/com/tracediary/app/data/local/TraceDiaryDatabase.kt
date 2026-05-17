package com.tracediary.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DiaryEntryEntity::class,
        AnnualSummaryEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class TraceDiaryDatabase : RoomDatabase() {
    abstract fun diaryEntryDao(): DiaryEntryDao

    abstract fun annualSummaryDao(): AnnualSummaryDao

    abstract fun syncMetadataDao(): SyncMetadataDao
}
