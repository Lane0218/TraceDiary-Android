package com.tracediary.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnualSummaryDao {
    @Query("SELECT * FROM annual_summaries WHERE year = :year")
    fun observeByYear(year: Int): Flow<AnnualSummaryEntity?>

    @Query("SELECT * FROM annual_summaries WHERE year = :year")
    suspend fun getByYear(year: Int): AnnualSummaryEntity?

    @Query("SELECT * FROM annual_summaries ORDER BY year DESC")
    fun observeAll(): Flow<List<AnnualSummaryEntity>>

    @Query("SELECT * FROM annual_summaries ORDER BY year DESC")
    suspend fun getAll(): List<AnnualSummaryEntity>

    @Query("DELETE FROM annual_summaries WHERE year = :year")
    suspend fun deleteByYear(year: Int)

    @Upsert
    suspend fun upsert(entity: AnnualSummaryEntity)
}
