package com.tracediary.app.domain.repository

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

interface AnnualSummaryRepository {
    fun observeSummary(year: Int): Flow<AnnualSummary?>

    fun observeSummaries(): Flow<List<AnnualSummary>>

    suspend fun getSummary(year: Int): AnnualSummary?

    suspend fun saveSummary(year: Int, content: String): AnnualSummary

    /**
     * 导入专用写入：保留外部传入的时间戳与同步状态。默认实现退化为 [saveSummary]，
     * 生产实现 [com.tracediary.app.data.repository.DefaultAnnualSummaryRepository] 必须重写此方法。
     */
    suspend fun upsertImportedSummary(summary: AnnualSummary): AnnualSummary =
        saveSummary(summary.year, summary.content)

    suspend fun deleteSummary(year: Int)

    /**
     * 更新本地年度总结的同步状态。条目不存在时静默忽略。
     *
     * 默认空实现：旧的 fake/test 实现无需为同步功能补 override。
     * 生产实现 [com.tracediary.app.data.repository.DefaultAnnualSummaryRepository] 必须重写此方法。
     */
    suspend fun setSyncState(year: Int, state: SyncState) = Unit
}
