package com.tracediary.app.data.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

/**
 * 密码验证器（wrapped DEK）的纯算法实现，独立于 Android Keystore 等系统依赖，
 * 便于在 JVM 单元测试中验证 AES-GCM 包裹/解包格式。
 *
 * 验证器格式：`[1B 版本=1][12B IV][密文+16B GCM tag]`
 */
internal object PasswordVerifierCodec {

    const val VERSION: Byte = 1
    const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun wrap(kek: SecretKey, payload: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(payload)
        return ByteArray(1 + iv.size + ct.size).also { out ->
            out[0] = VERSION
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
        }
    }

    fun unwrap(kek: SecretKey, raw: ByteArray): ByteArray {
        require(raw.isNotEmpty() && raw[0] == VERSION) { "未知的验证器版本" }
        require(raw.size > 1 + IV_BYTES) { "验证器长度不足" }
        val iv = raw.copyOfRange(1, 1 + IV_BYTES)
        val ct = raw.copyOfRange(1 + IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
