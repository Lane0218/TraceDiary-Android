package com.tracediary.app.data.sync

import kotlinx.serialization.Serializable

/**
 * 同步对象的加密信封：把元数据放在外层明文，正文 [cipher] 是 AES-GCM Base64 密文。
 *
 * 元数据明文上云的原因：
 * - SyncEngine 的 last-write-wins 必须在不解密的情况下能比 [updatedAt]，
 *   否则只要拿不到密码就连同步策略都无法运转。
 * - [date]/[year] 是文件名的天然映射，藏起来也没意义。
 *
 * 旧版（W1）上传过的明文 JSON 用 [SyncDiaryDto]/[SyncAnnualDto] 直接序列化，没有 cipher 字段。
 * 解码时按"是否含 cipher"分流：含则信封解密，不含按旧明文反序列化。
 */
@Serializable
data class DiaryEnvelope(
    val v: Int = 1,
    val date: String,
    val updatedAt: String,
    val cipher: String,
)

@Serializable
data class AnnualEnvelope(
    val v: Int = 1,
    val year: Int,
    val updatedAt: String,
    val cipher: String,
)
