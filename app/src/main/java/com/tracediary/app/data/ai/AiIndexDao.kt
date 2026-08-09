package com.tracediary.app.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AiIndexDao {
    @Query("SELECT * FROM ai_index_chunks")
    suspend fun getAllChunks(): List<AiIndexEntity>

    @Query("SELECT * FROM ai_index_chunks WHERE sourceType = :sourceType AND sourceKey = :sourceKey ORDER BY chunkIndex")
    suspend fun getSourceChunks(sourceType: String, sourceKey: String): List<AiIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<AiIndexEntity>)

    @Query("DELETE FROM ai_index_chunks WHERE sourceType = :sourceType AND sourceKey = :sourceKey")
    suspend fun deleteSource(sourceType: String, sourceKey: String)

    @Query("DELETE FROM ai_index_chunks")
    suspend fun deleteAllChunks()

    @Query("SELECT * FROM ai_index_metadata WHERE `key` = 'index'")
    suspend fun getMetadata(): AiIndexMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: AiIndexMetadataEntity)

    @Transaction
    suspend fun replaceSource(sourceType: String, sourceKey: String, chunks: List<AiIndexEntity>) {
        deleteSource(sourceType, sourceKey)
        if (chunks.isNotEmpty()) upsertChunks(chunks)
    }
}
