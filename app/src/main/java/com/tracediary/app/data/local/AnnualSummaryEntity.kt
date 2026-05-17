package com.tracediary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.SyncState
import java.time.Instant

@Entity(tableName = "annual_summaries")
data class AnnualSummaryEntity(
    @PrimaryKey val year: Int,
    val content: String,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
    val syncState: SyncState
) {
    fun toDomain(): AnnualSummary {
        return AnnualSummary(
            year = year,
            content = content,
            createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMilli),
            syncState = syncState
        )
    }

    companion object {
        fun fromDomain(summary: AnnualSummary): AnnualSummaryEntity {
            return AnnualSummaryEntity(
                year = summary.year,
                content = summary.content,
                createdAtEpochMilli = summary.createdAt.toEpochMilli(),
                updatedAtEpochMilli = summary.updatedAt.toEpochMilli(),
                syncState = summary.syncState
            )
        }
    }
}
