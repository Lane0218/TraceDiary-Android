package com.tracediary.app.domain.model

import java.time.Instant

data class SyncMetadata(
    val objectKey: String,
    val localUpdatedAt: Instant,
    val remoteUpdatedAt: Instant?,
    val lastSyncedAt: Instant?,
    val checksum: String?
)
