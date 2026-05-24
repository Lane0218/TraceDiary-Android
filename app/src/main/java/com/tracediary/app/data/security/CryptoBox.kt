package com.tracediary.app.data.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * 应用密码加密工具。
 *
 * 唯一秘密是用户应用密码；不同用途通过固定公开 salt 隔离。固定 salt 与 KDF 参数
 * 是数据协议的一部分，发布后不能随意修改。
 *
 * AES-GCM 每次加密生成随机 12 字节 IV，输出格式：
 *
 *   [12B IV][密文+16B GCM tag]
 *
 * 之所以把 IV 放到密文头里，是因为同一条日记多次更新会用同一个 key 反复加密，
 * 必须保证 IV 不重复——把 IV 与密文一起存下来，解密侧不需要外部传 IV。
 */
object CryptoBox {

    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val DATABASE_PASSPHRASE_PREFIX = "td-db-v3:"

    private val CLOUD_KEY_SALT = "TraceDiary Android Cloud Key".toByteArray(Charsets.UTF_8)
    private val DATABASE_KEY_SALT = "TraceDiary Android Database Key".toByteArray(Charsets.UTF_8)
    private val PASSWORD_VERIFIER_SALT = "TraceDiary Android Password Verifier".toByteArray(Charsets.UTF_8)

    private val passwordVerifierPayload =
        "TraceDiary Android Password Verifier Payload".toByteArray(Charsets.UTF_8)

    /** 云端对象内容加密 key。耗时操作，应在 IO 线程调用。 */
    fun deriveCloudKey(password: String): SecretKey =
        deriveKey(password, CLOUD_KEY_SALT)

    /** 密码验证器加密 key。耗时操作，应在 IO 线程调用。 */
    fun derivePasswordVerifierKey(password: String): SecretKey =
        deriveKey(password, PASSWORD_VERIFIER_SALT)

    /**
     * SQLCipher passphrase。返回 ASCII 字符串的 UTF-8 字节，便于 Room 打开与
     * `PRAGMA rekey` 使用同一套确定性口令。
     */
    fun deriveDatabasePassphrase(password: String): ByteArray =
        deriveDatabasePassphraseString(password).toByteArray(Charsets.UTF_8)

    fun deriveDatabasePassphraseString(password: String): String {
        val raw = deriveKey(password, DATABASE_KEY_SALT).encoded
        return DATABASE_PASSPHRASE_PREFIX + Base64.getEncoder().encodeToString(raw)
    }

    fun verifierPayload(): ByteArray = passwordVerifierPayload.copyOf()

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
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
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return Base64.getEncoder().encodeToString(out)
    }

    /** 解密 [encryptToBase64] 输出。失败抛异常（GCM tag 不通过、Base64 损坏等）。 */
    fun decryptFromBase64(key: SecretKey, base64: String): ByteArray {
        val raw = Base64.getDecoder().decode(base64)
        require(raw.size > IV_BYTES) { "密文长度不足" }
        val iv = raw.copyOfRange(0, IV_BYTES)
        val ct = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
