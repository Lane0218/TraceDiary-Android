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
 * 旧版明文 JSON、带版本/随机 salt 的旧信封不再兼容；新安装和新云端数据从当前格式开始。
 */
@Serializable
data class DiaryEnvelope(
    val date: String,
    val updatedAt: String,
    val cipher: String,
)

@Serializable
data class AnnualEnvelope(
    val year: Int,
    val updatedAt: String,
    val cipher: String,
)
