package com.tracediary.app.data.repository

import com.tracediary.app.data.local.SyncMetadataDao
import com.tracediary.app.data.local.SyncMetadataEntity
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSyncMetadataRepository(
    private val syncMetadataDao: SyncMetadataDao
) : SyncMetadataRepository {
    override fun observeMetadata(objectKey: String): Flow<SyncMetadata?> {
        return syncMetadataDao.observeByObjectKey(objectKey).map { it?.toDomain() }
    }

    override suspend fun getMetadata(objectKey: String): SyncMetadata? {
        return syncMetadataDao.getByObjectKey(objectKey)?.toDomain()
    }

    override suspend fun saveMetadata(metadata: SyncMetadata) {
        syncMetadataDao.upsert(SyncMetadataEntity.fromDomain(metadata))
    }

    override suspend fun deleteMetadata(objectKey: String) {
        syncMetadataDao.deleteByObjectKey(objectKey)
    }
}
