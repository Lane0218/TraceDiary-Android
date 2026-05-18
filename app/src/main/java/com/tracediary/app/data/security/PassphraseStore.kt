package com.tracediary.app.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * 本地 SQLCipher passphrase 管理。
 *
 * 通过 Android Jetpack Security 提供的 EncryptedSharedPreferences 安全存储一个
 * 32 字节随机 passphrase，首次访问时生成，后续读取直接复用。passphrase 仅以
 * ByteArray 形式短暂返回，避免长期持有 String 拷贝。
 */
class PassphraseStore(context: Context) {

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

    /**
     * 返回 SQLCipher 使用的 passphrase。首次调用时随机生成 32 字节并保存，
     * 之后调用直接读取已有 passphrase。
     */
    @Synchronized
    fun getOrCreate(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val generated = ByteArray(PASSPHRASE_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(generated, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PASSPHRASE, encoded).apply()
        return generated
    }

    private companion object {
        const val PREF_FILE_NAME = "tracediary_secure_prefs"
        const val KEY_PASSPHRASE = "db_passphrase"
        const val PASSPHRASE_BYTE_LENGTH = 32
    }
}
