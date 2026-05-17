package com.tracediary.app.data.local

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test
    fun diaryEntryEntity_roundTripsDomainModel() {
        val entry = DiaryEntry(
            date = LocalDate.parse("2026-05-17"),
            content = "今天\n继续记录",
            createdAt = Instant.parse("2026-05-17T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-17T09:00:00Z"),
            syncState = SyncState.Pending
        )

        val entity = DiaryEntryEntity.fromDomain(entry)

        assertEquals(entry, entity.toDomain())
        assertEquals(entry.date.toEpochDay(), entity.epochDay)
    }

    @Test
    fun annualSummaryEntity_roundTripsDomainModel() {
        val summary = AnnualSummary(
            year = 2026,
            content = "年度总结正文",
            createdAt = Instant.parse("2026-12-31T08:00:00Z"),
            updatedAt = Instant.parse("2026-12-31T09:00:00Z"),
            syncState = SyncState.Synced
        )

        val entity = AnnualSummaryEntity.fromDomain(summary)

        assertEquals(summary, entity.toDomain())
        assertEquals(2026, entity.year)
    }

    @Test
    fun syncMetadataEntity_roundTripsNullableFields() {
        val metadata = SyncMetadata(
            objectKey = "diary/2026-05-17.json",
            localUpdatedAt = Instant.parse("2026-05-17T09:00:00Z"),
            remoteUpdatedAt = null,
            lastSyncedAt = Instant.parse("2026-05-17T09:01:00Z"),
            checksum = null
        )

        val entity = SyncMetadataEntity.fromDomain(metadata)

        assertEquals(metadata, entity.toDomain())
        assertEquals(metadata.objectKey, entity.objectKey)
    }
}
