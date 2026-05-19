package com.tracediary.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE objectKey = :objectKey")
    fun observeByObjectKey(objectKey: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE objectKey = :objectKey")
    suspend fun getByObjectKey(objectKey: String): SyncMetadataEntity?

    @Query("SELECT MAX(lastSyncedAtEpochMilli) FROM sync_metadata")
    fun observeLatestLastSyncedAtEpochMilli(): Flow<Long?>

    @Upsert
    suspend fun upsert(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE objectKey = :objectKey")
    suspend fun deleteByObjectKey(objectKey: String)
}
