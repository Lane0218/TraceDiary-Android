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

/** 备份 ZIP 包顶层 manifest，记录元数据与负载类型。 */
@kotlinx.serialization.Serializable
data class BackupManifest(
    val version: Int = 1,
    val exportedAt: String,
    /** json / txt / md，仅作为提示，导入不依赖此字段。 */
    val payloadKind: String,
)

/** 导出格式。 */
enum class ExportFormat {
    Json,
    TextZip,
    MarkdownZip,
}

/** 导出结果：字节内容 + 推荐 MIME + 推荐文件名。 */
data class ExportPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val suggestedFileName: String,
    val entryCount: Int,
    val summaryCount: Int,
)
