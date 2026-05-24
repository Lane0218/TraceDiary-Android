package com.tracediary.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface CloudCipherCredentialStore {
    fun saveLocalPassword(value: String?)
    fun loadLocalPassword(): String?
}

/**
 * 云同步敏感字段（AccessKey）与本机便利密码缓存。
 * 本机缓存只用于“记住密码”，不参与数据可恢复性。
 */
class SecureCredentialStore(context: Context) : CloudCipherCredentialStore {

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
    override fun saveLocalPassword(value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(KEY_LOCAL_PASSWORD).apply()
        } else {
            prefs.edit().putString(KEY_LOCAL_PASSWORD, value).apply()
        }
    }

    @Synchronized
    override fun loadLocalPassword(): String? {
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
