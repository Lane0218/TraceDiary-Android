package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.SyncMetadata
import kotlinx.coroutines.flow.Flow

interface SyncMetadataRepository {
    fun observeMetadata(objectKey: String): Flow<SyncMetadata?>

    suspend fun getMetadata(objectKey: String): SyncMetadata?

    suspend fun saveMetadata(metadata: SyncMetadata)

    suspend fun deleteMetadata(objectKey: String)
}
