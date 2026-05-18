package com.tracediary.app.data.sync

import java.time.Instant

/**
 * 远端云端同步相关的数据模型与结果类型。
 *
 * 注意：[RemoteObject] 中包含 [java.time.Instant] 字段，无法稳定 kotlinx.serialization，
 * 因此不加 @Serializable；[RemoteCredential] 的持久化方式由调用方决定（建议放入
 * 已有的加密 DataStore），这里也不加序列化注解，避免暴露 secret。
 */

/** 支持的远端对象存储 provider。 */
enum class SyncProvider {
    ALIYUN_OSS,
    TENCENT_COS,
}

/** 错误分类，用于 UI / 上层重试策略。 */
enum class ErrorCode {
    /** 网络抖动 / 5xx / IOException 等可重试的临时错误。 */
    NETWORK,
    /** 401 / 403 / 签名错误 / AK SK 无效。 */
    AUTH,
    /** 404 对象或 bucket 不存在。 */
    NOT_FOUND,
    /** 412 If-Match 失败等乐观并发冲突。 */
    CONFLICT,
    /** 解析失败、未分类错误。 */
    UNKNOWN,
}

/** 远端对象元信息。 */
data class RemoteObject(
    val key: String,
    val etag: String?,
    val lastModified: Instant?,
    val sizeBytes: Long,
)

/**
 * 单个远端 bucket 的访问凭证。
 *
 * @param region OSS 形如 oss-cn-hangzhou，COS 形如 ap-guangzhou。
 * @param bucket OSS 形如 my-bucket，COS 形如 tracediary-1300000000（含 APPID）。
 * @param pathPrefix 可选 key 前缀，所有读写键名会拼接在它后面。
 */
data class RemoteCredential(
    val provider: SyncProvider,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val accessKeySecret: String,
    val pathPrefix: String = "",
)

/** 统一的远端调用结果。 */
sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>
    data class Err(
        val code: ErrorCode,
        val message: String,
        val cause: Throwable? = null,
    ) : RemoteResult<Nothing>
}
