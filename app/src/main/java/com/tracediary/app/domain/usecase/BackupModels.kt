package com.tracediary.app.domain.usecase

/**
 * 备份/恢复使用的数据快照模型。
 *
 * 与 Domain 模型分离，便于：
 *  - 在导出格式中固定字段名与字符串编码（ISO-8601 / yyyy-MM-dd），不被实体的字段重构影响。
 *  - 字段缺失时由 [kotlinx.serialization] 默认值兜底（例如未来加新字段，旧备份依然可读）。
 */
@kotlinx.serialization.Serializable
data class DiaryBackup(
    val version: Int = 1,
    val exportedAt: String,
    val entries: List<DiaryEntryBackup> = emptyList(),
    val annualSummaries: List<AnnualSummaryBackup> = emptyList(),
)

@kotlinx.serialization.Serializable
data class DiaryEntryBackup(
    val date: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)

@kotlinx.serialization.Serializable
data class AnnualSummaryBackup(
    val year: Int,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)
