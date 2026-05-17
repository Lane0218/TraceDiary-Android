package com.tracediary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val epochDay: Long,
    val content: String,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
    val syncState: SyncState
) {
    fun toDomain(): DiaryEntry {
        return DiaryEntry(
            date = LocalDate.ofEpochDay(epochDay),
            content = content,
            createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMilli),
            syncState = syncState
        )
    }

    companion object {
        fun fromDomain(entry: DiaryEntry): DiaryEntryEntity {
            return DiaryEntryEntity(
                epochDay = entry.date.toEpochDay(),
                content = entry.content,
                createdAtEpochMilli = entry.createdAt.toEpochMilli(),
                updatedAtEpochMilli = entry.updatedAt.toEpochMilli(),
                syncState = entry.syncState
            )
        }
    }
}
