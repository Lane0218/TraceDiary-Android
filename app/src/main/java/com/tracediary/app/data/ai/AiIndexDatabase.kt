package com.tracediary.app.data.ai

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AiIndexEntity::class, AiIndexMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AiIndexDatabase : RoomDatabase() {
    abstract fun aiIndexDao(): AiIndexDao
}
