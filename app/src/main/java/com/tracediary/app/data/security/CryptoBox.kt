package com.tracediary.app.data.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * 同步内容加密工具：PBKDF2WithHmacSHA256(passphrase, salt, 100k 轮)派生 256 位 key，
 * 然后用 AES/GCM/NoPadding 加密。每次加密生成随机 12 字节 IV，输出格式：
 *
 *   [1B 版本=1][12B IV][密文+16B GCM tag]
 *
 * 之所以把 IV 放到密文头里，是因为同一条日记多次更新会用同一个 key 反复加密，
 * 必须保证 IV 不重复——把 IV 与密文一起存下来，解密侧不需要外部传 IV。
 */
object CryptoBox {

    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val VERSION: Byte = 1

    /** 用 [passphrase] 与 [salt] 派生 AES-256 密钥。耗时操作，应在 IO 线程调用。 */
    fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** 加密任意明文，返回 Base64 字符串便于嵌入 JSON 信封。 */
    fun encryptToBase64(key: SecretKey, plaintext: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        val out = ByteArray(1 + iv.size + ct.size)
        out[0] = VERSION
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** 解密 [encryptToBase64] 输出。失败抛异常（GCM tag 不通过、版本不匹配、Base64 损坏等）。 */
    fun decryptFromBase64(key: SecretKey, base64: String): ByteArray {
        val raw = Base64.decode(base64, Base64.NO_WRAP)
        require(raw.isNotEmpty() && raw[0] == VERSION) { "未知或不兼容的加密版本" }
        require(raw.size > 1 + IV_BYTES) { "密文长度不足" }
        val iv = raw.copyOfRange(1, 1 + IV_BYTES)
        val ct = raw.copyOfRange(1 + IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
