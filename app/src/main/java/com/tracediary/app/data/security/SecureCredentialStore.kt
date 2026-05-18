package com.tracediary.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 云同步敏感字段（AccessKey / 本机数据密码）安全存储。
 *
 * 与 [PassphraseStore] 隔离：使用独立的 prefs 文件 [PREF_FILE_NAME]，
 * 避免数据库 passphrase 与云凭证写到同一个加密文件，便于分别清空与排错。
 */
class SecureCredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            PREF_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** 保存云同步 AK/SK 对。两者任意一个为空都不写入。 */
    @Synchronized
    fun saveCloudCredential(id: String, secret: String) {
        if (id.isBlank() || secret.isBlank()) return
        prefs.edit()
            .putString(KEY_ACCESS_KEY_ID, id)
            .putString(KEY_ACCESS_KEY_SECRET, secret)
            .apply()
    }

    /** 读取云凭证；任一字段缺失返回 null。 */
    @Synchronized
    fun loadCloudCredential(): Pair<String, String>? {
        val id = prefs.getString(KEY_ACCESS_KEY_ID, null)
        val secret = prefs.getString(KEY_ACCESS_KEY_SECRET, null)
        return if (id.isNullOrBlank() || secret.isNullOrBlank()) null else id to secret
    }

    @Synchronized
    fun clearCloudCredential() {
        prefs.edit()
            .remove(KEY_ACCESS_KEY_ID)
            .remove(KEY_ACCESS_KEY_SECRET)
            .apply()
    }

    /** 本机保存的数据密码；传 null / 空字符串等价于清除。 */
    @Synchronized
    fun saveLocalPassword(value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(KEY_LOCAL_PASSWORD).apply()
        } else {
            prefs.edit().putString(KEY_LOCAL_PASSWORD, value).apply()
        }
    }

    @Synchronized
    fun loadLocalPassword(): String? {
        val value = prefs.getString(KEY_LOCAL_PASSWORD, null)
        return if (value.isNullOrEmpty()) null else value
    }

    /**
     * 同步加密用的 salt。首次调用时随机生成 16 字节 salt 并持久化，之后直接读取。
     * 与本机密码分开：salt 不是机密，但变更会让所有云端密文不可解，因此一旦生成不修改。
     * 修改密码时 [rotateCloudCipherSalt] 会显式生成新 salt 并触发 rekey 流程。
     */
    @Synchronized
    fun loadOrCreateCloudCipherSalt(): ByteArray {
        val existing = prefs.getString(KEY_CLOUD_CIPHER_SALT, null)
        if (existing != null) return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        val generated = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_CLOUD_CIPHER_SALT, android.util.Base64.encodeToString(generated, android.util.Base64.NO_WRAP))
            .apply()
        return generated
    }

    /** 修改同步密码时生成新的 salt。调用方负责用旧 key 解远端、用新 key 重新上传。 */
    @Synchronized
    fun rotateCloudCipherSalt(): ByteArray {
        val generated = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_CLOUD_CIPHER_SALT, android.util.Base64.encodeToString(generated, android.util.Base64.NO_WRAP))
            .apply()
        return generated
    }

    private companion object {
        const val PREF_FILE_NAME = "tracediary_credentials_prefs"
        const val KEY_ACCESS_KEY_ID = "cloud_access_key_id"
        const val KEY_ACCESS_KEY_SECRET = "cloud_access_key_secret"
        const val KEY_LOCAL_PASSWORD = "local_password"
        const val KEY_CLOUD_CIPHER_SALT = "cloud_cipher_salt"
    }
}
