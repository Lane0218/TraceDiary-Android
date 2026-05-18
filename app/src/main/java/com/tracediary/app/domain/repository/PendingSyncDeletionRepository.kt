package com.tracediary.app.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 已本地删除但尚未成功同步删除的目标记录。
 *
 * SyncEngine 在单条远端删除失败时写入；后续 mergeAll/uploadAll 周期性地重试。
 */
interface PendingSyncDeletionRepository {
    fun observeAll(): Flow<List<PendingSyncDeletion>>

    suspend fun getAll(): List<PendingSyncDeletion>

    suspend fun markDiaryPending(date: LocalDate)

    suspend fun markAnnualPending(year: Int)

    suspend fun clearDiary(date: LocalDate)

    suspend fun clearAnnual(year: Int)
}

/** 待重试的远端删除任务，统一暴露 objectKey + 业务键。 */
sealed interface PendingSyncDeletion {
    val objectKey: String

    data class Diary(override val objectKey: String, val date: LocalDate) : PendingSyncDeletion
    data class Annual(override val objectKey: String, val year: Int) : PendingSyncDeletion
}
