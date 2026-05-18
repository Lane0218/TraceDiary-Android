package com.tracediary.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDeletionDao {
    @Query("SELECT * FROM pending_sync_deletions ORDER BY createdAtEpochMilli ASC")
    suspend fun getAll(): List<PendingSyncDeletionEntity>

    @Query("SELECT * FROM pending_sync_deletions ORDER BY createdAtEpochMilli ASC")
    fun observeAll(): Flow<List<PendingSyncDeletionEntity>>

    @Upsert
    suspend fun upsert(entity: PendingSyncDeletionEntity)

    @Query("DELETE FROM pending_sync_deletions WHERE objectKey = :objectKey")
    suspend fun deleteByObjectKey(objectKey: String)
}
