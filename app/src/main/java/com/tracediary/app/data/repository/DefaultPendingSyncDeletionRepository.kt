package com.tracediary.app.data.repository

import com.tracediary.app.data.local.PendingSyncDeletionDao
import com.tracediary.app.data.local.PendingSyncDeletionEntity
import com.tracediary.app.data.sync.SyncObjectKeys
import com.tracediary.app.domain.repository.PendingSyncDeletion
import com.tracediary.app.domain.repository.PendingSyncDeletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DefaultPendingSyncDeletionRepository(
    private val dao: PendingSyncDeletionDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) : PendingSyncDeletionRepository {

    override fun observeAll(): Flow<List<PendingSyncDeletion>> =
        dao.observeAll().map { list -> list.mapNotNull(::toDomain) }

    override suspend fun getAll(): List<PendingSyncDeletion> =
        dao.getAll().mapNotNull(::toDomain)

    override suspend fun markDiaryPending(date: LocalDate) {
        val targetKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsert(
            PendingSyncDeletionEntity(
                objectKey = SyncObjectKeys.diaryObjectKey(date),
                type = PendingSyncDeletionEntity.TYPE_DIARY,
                targetKey = targetKey,
                createdAtEpochMilli = clock.millis(),
            )
        )
    }

    override suspend fun markAnnualPending(year: Int) {
        dao.upsert(
            PendingSyncDeletionEntity(
                objectKey = SyncObjectKeys.annualObjectKey(year),
                type = PendingSyncDeletionEntity.TYPE_ANNUAL,
                targetKey = year.toString(),
                createdAtEpochMilli = clock.millis(),
            )
        )
    }

    override suspend fun clearDiary(date: LocalDate) {
        dao.deleteByObjectKey(SyncObjectKeys.diaryObjectKey(date))
    }

    override suspend fun clearAnnual(year: Int) {
        dao.deleteByObjectKey(SyncObjectKeys.annualObjectKey(year))
    }

    private fun toDomain(entity: PendingSyncDeletionEntity): PendingSyncDeletion? = when (entity.type) {
        PendingSyncDeletionEntity.TYPE_DIARY -> runCatching {
            PendingSyncDeletion.Diary(
                objectKey = entity.objectKey,
                date = LocalDate.parse(entity.targetKey, DateTimeFormatter.ISO_LOCAL_DATE),
            )
        }.getOrNull()
        PendingSyncDeletionEntity.TYPE_ANNUAL -> entity.targetKey.toIntOrNull()?.let { year ->
            PendingSyncDeletion.Annual(objectKey = entity.objectKey, year = year)
        }
        else -> null
    }
}
