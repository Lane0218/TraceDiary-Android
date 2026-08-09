package com.tracediary.app.data.ai

import androidx.room.Entity

@Entity(
    tableName = "ai_index_chunks",
    primaryKeys = ["sourceType", "sourceKey", "chunkIndex"],
)
data class AiIndexEntity(
    val sourceType: String,
    val sourceKey: String,
    val chunkIndex: Int,
    val sourceContentHash: String,
    val sourceUpdatedAtEpochMillis: Long,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val embedding: ByteArray,
    val modelId: String,
)

@Entity(tableName = "ai_index_metadata")
data class AiIndexMetadataEntity(
    @androidx.room.PrimaryKey val key: String = "index",
    val modelId: String,
    val sourceSignature: String,
    val complete: Boolean,
    val updatedAtEpochMillis: Long,
)
