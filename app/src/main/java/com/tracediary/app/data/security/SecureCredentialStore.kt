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

    private companion object {
        const val PREF_FILE_NAME = "tracediary_credentials_prefs"
        const val KEY_ACCESS_KEY_ID = "cloud_access_key_id"
        const val KEY_ACCESS_KEY_SECRET = "cloud_access_key_secret"
        const val KEY_LOCAL_PASSWORD = "local_password"
    }
}
