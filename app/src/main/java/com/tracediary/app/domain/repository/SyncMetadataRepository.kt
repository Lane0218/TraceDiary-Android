package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.SyncMetadata
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SyncMetadataRepository {
    fun observeMetadata(objectKey: String): Flow<SyncMetadata?>

    fun observeLatestLastSyncedAt(): Flow<Instant?>

    suspend fun getMetadata(objectKey: String): SyncMetadata?

    suspend fun saveMetadata(metadata: SyncMetadata)

    suspend fun deleteMetadata(objectKey: String)
}
