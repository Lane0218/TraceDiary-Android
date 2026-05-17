package com.tracediary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tracediary.app.domain.model.SyncMetadata
import java.time.Instant

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val objectKey: String,
    val localUpdatedAtEpochMilli: Long,
    val remoteUpdatedAtEpochMilli: Long?,
    val lastSyncedAtEpochMilli: Long?,
    val checksum: String?
) {
    fun toDomain(): SyncMetadata {
        return SyncMetadata(
            objectKey = objectKey,
            localUpdatedAt = Instant.ofEpochMilli(localUpdatedAtEpochMilli),
            remoteUpdatedAt = remoteUpdatedAtEpochMilli?.let(Instant::ofEpochMilli),
            lastSyncedAt = lastSyncedAtEpochMilli?.let(Instant::ofEpochMilli),
            checksum = checksum
        )
    }

    companion object {
        fun fromDomain(metadata: SyncMetadata): SyncMetadataEntity {
            return SyncMetadataEntity(
                objectKey = metadata.objectKey,
                localUpdatedAtEpochMilli = metadata.localUpdatedAt.toEpochMilli(),
                remoteUpdatedAtEpochMilli = metadata.remoteUpdatedAt?.toEpochMilli(),
                lastSyncedAtEpochMilli = metadata.lastSyncedAt?.toEpochMilli(),
                checksum = metadata.checksum
            )
        }
    }
}
