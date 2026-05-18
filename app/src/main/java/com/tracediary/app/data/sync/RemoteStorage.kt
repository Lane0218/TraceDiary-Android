package com.tracediary.app.data.sync

/**
 * 远端对象存储统一抽象。所有方法都是 suspend，实现内部应使用
 * `withContext(Dispatchers.IO)` 包住网络调用，且不得把 secret 写入日志。
 *
 * key 命名约定：调用方传入相对 [RemoteCredential.pathPrefix] 的 key，
 * 实现负责拼接最终的远端绝对路径。
 */
interface RemoteStorage {

    /**
     * 上传对象。
     *
     * @param ifMatchEtag 乐观并发：仅当远端对象 ETag 与该值一致时才覆盖；
     *   传 null 表示无条件覆盖（也可用于首次创建）。
     *   远端返回 412 时映射为 [ErrorCode.CONFLICT]。
     */
    suspend fun put(
        key: String,
        body: ByteArray,
        contentType: String = "application/octet-stream",
        ifMatchEtag: String? = null,
    ): RemoteResult<RemoteObject>

    /** 下载对象内容。 */
    suspend fun get(key: String): RemoteResult<ByteArray>

    /** 仅获取对象元信息，不下载 body。 */
    suspend fun head(key: String): RemoteResult<RemoteObject>

    /** 列出 prefix 下的对象，prefix 会与 [RemoteCredential.pathPrefix] 拼接。 */
    suspend fun list(prefix: String = ""): RemoteResult<List<RemoteObject>>

    /** 删除对象，404 视为成功（幂等）。 */
    suspend fun delete(key: String): RemoteResult<Unit>

    /**
     * 用于"保存并验证连接"：发起一次低成本的列表请求，
     * 任何 200 响应都视为通；4xx/5xx/IOException 走错误码映射。
     */
    suspend fun ping(): RemoteResult<Unit>
}
