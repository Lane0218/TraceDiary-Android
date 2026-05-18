package com.tracediary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待重试的远端删除记录。
 *
 * - 类型 [TYPE_DIARY] 时 `targetKey` 为 `yyyy-MM-dd`，对应 [com.tracediary.app.data.sync.SyncObjectKeys.diaryObjectKey]。
 * - 类型 [TYPE_ANNUAL] 时 `targetKey` 为 `yyyy`，对应 [com.tracediary.app.data.sync.SyncObjectKeys.annualObjectKey]。
 *
 * 使用 `(type, targetKey)` 联合主键即可保证一条记录唯一；Room 主键仅支持单字段，
 * 这里把两段拼接成 [objectKey] 直接作为主键，便于 SyncEngine 重试时按 key 删除。
 */
@Entity(tableName = "pending_sync_deletions")
data class PendingSyncDeletionEntity(
    @PrimaryKey val objectKey: String,
    val type: String,
    val targetKey: String,
    val createdAtEpochMilli: Long,
) {
    companion object {
        const val TYPE_DIARY = "diary"
        const val TYPE_ANNUAL = "annual"
    }
}
