package com.tracediary.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryEntryDao {
    @Query("SELECT * FROM diary_entries WHERE epochDay = :epochDay")
    fun observeByDate(epochDay: Long): Flow<DiaryEntryEntity?>

    @Query("SELECT * FROM diary_entries WHERE epochDay = :epochDay")
    suspend fun getByDate(epochDay: Long): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    fun observeBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    suspend fun getBetween(startEpochDay: Long, endEpochDay: Long): List<DiaryEntryEntity>

    @Query("SELECT epochDay FROM diary_entries ORDER BY epochDay ASC")
    fun observeRecordedEpochDays(): Flow<List<Long>>

    @Query(
        """
        SELECT * FROM diary_entries
        WHERE content LIKE '%' || :query || '%'
        AND (:startEpochDay IS NULL OR epochDay >= :startEpochDay)
        AND (:endEpochDay IS NULL OR epochDay <= :endEpochDay)
        ORDER BY epochDay DESC
        """
    )
    suspend fun search(query: String, startEpochDay: Long?, endEpochDay: Long?): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entries WHERE epochDay = :epochDay")
    suspend fun deleteByDate(epochDay: Long)

    @Upsert
    suspend fun upsert(entity: DiaryEntryEntity)

    @Delete
    suspend fun delete(entity: DiaryEntryEntity)
}
