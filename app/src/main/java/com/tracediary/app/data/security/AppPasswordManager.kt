package com.tracediary.app.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用密码 / 数据密钥管理。
 *
 * 模型：
 * - 数据库密钥（DEK）由 [PassphraseStore] 持有的 32 字节随机串提供，DEK 的存在不依赖应用密码。
 * - 应用密码派生 KEK（PBKDF2）后，用 AES-GCM 把 DEK 包成"验证器"（wrapped DEK）持久化在
 *   独立的 EncryptedSharedPreferences 文件中。验证器仅用于校验密码正确性，不影响 DB 实际打开。
 * - 同时保留与现有 [CloudCipherCredentialStore.loadOrCreateCloudCipherSalt] 配合的"云密码"，
 *   等价于应用密码——一处密码同时承担"App 解锁 + 数据密钥保护 + 云端加密"三个职责。
 *
 * 状态：
 * - [AppLockState.NeedSetup]：未设置应用密码（首次启动 / 升级清库）。
 * - [AppLockState.Locked]：已设置但当前未解锁。
 * - [AppLockState.Unlocked]：已解锁，UI 可正常进入。
 */
class AppPasswordManager(
    context: Context,
    private val credentialStore: SecureCredentialStore,
    private val passphraseStore: PassphraseStore = PassphraseStore(context),
) {

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

    @Volatile private var unlockedFlag: Boolean = false

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    init {
        // 启动时若已记住密码，可自动解锁；解锁失败保持 Locked，等待用户输入。
        if (isPasswordConfigured() && isRememberPasswordEnabled()) {
            credentialStore.loadLocalPassword()?.let { stored ->
                runCatching { verifyPassword(stored) }
                    .onSuccess { ok -> if (ok) unlockedFlag = true }
            }
        }
        publishState()
    }

    fun isUnlocked(): Boolean = unlockedFlag

    fun needsSetup(): Boolean = !isPasswordConfigured()

    fun isRememberPasswordEnabled(): Boolean =
        prefs.getBoolean(KEY_REMEMBER_PASSWORD, true)

    /** 首次设置应用密码：生成验证器并按需记住密码。返回 true 表示已成功落库，false 表示密码空。 */
    fun setupInitialPassword(password: String, remember: Boolean): Result<Unit> {
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("密码不能为空"))
        if (isPasswordConfigured()) return Result.failure(IllegalStateException("已设置应用密码，请使用修改密码"))
        return runCatching {
            // DEK 取自现有 PassphraseStore（与 SQLCipher 实际打开使用的字节串一致）。
            val dek = passphraseStore.getOrCreate()
            val salt = credentialStore.loadOrCreateCloudCipherSalt()
            val kek = CryptoBox.deriveKey(password, salt)
            val verifier = wrapDek(kek, dek)
            prefs.edit()
                .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
                .putBoolean(KEY_REMEMBER_PASSWORD, remember)
                .apply()
            credentialStore.saveLocalPassword(if (remember) password else null)
            unlockedFlag = true
            publishState()
        }
    }

    /** 输入应用密码解锁。失败时返回带消息的 Result.failure，不修改任何状态。 */
    fun unlock(password: String): Result<Unit> {
        if (!isPasswordConfigured()) return Result.failure(IllegalStateException("尚未设置应用密码"))
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("密码不能为空"))
        return runCatching {
            val ok = verifyPassword(password)
            if (!ok) throw IllegalArgumentException("密码错误")
            unlockedFlag = true
            // 解锁成功且当前是"记住密码"配置，把内存中的密码同步到 SecureCredentialStore，
            // 让 SyncEngine 等同步组件能用同一个密码加密云端对象。
            if (isRememberPasswordEnabled()) {
                credentialStore.saveLocalPassword(password)
            }
            publishState()
        }
    }

    /** 立即上锁。内存态 [unlockedFlag] 置 false；持久化的验证器与 salt 不变。 */
    fun lockNow() {
        unlockedFlag = false
        publishState()
    }

    /** 切换"在本机记住密码"。关闭时立即抹掉 SecureCredentialStore 里的密码缓存。 */
    fun setRememberPasswordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_PASSWORD, enabled).apply()
        if (!enabled) credentialStore.saveLocalPassword(null)
    }

    /**
     * 修改应用密码：先校验旧密码 → 重新包裹 DEK → 让调用方触发云端 rekey。
     *
     * 若云端 rekey 由 [com.tracediary.app.data.sync.SyncEngine.rotateSyncPassword] 完成，
     * 调用方应在本方法返回 success 后再调用 SyncEngine 同步轮换；
     * 这里只负责本地验证器的更新，因此本地失败不会影响云端。
     */
    fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        if (newPassword.isEmpty()) return Result.failure(IllegalArgumentException("新密码不能为空"))
        if (!verifyPassword(oldPassword)) return Result.failure(IllegalArgumentException("旧密码错误"))
        return runCatching {
            val dek = passphraseStore.getOrCreate()
            val salt = credentialStore.loadOrCreateCloudCipherSalt()
            val kek = CryptoBox.deriveKey(newPassword, salt)
            val verifier = wrapDek(kek, dek)
            prefs.edit()
                .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
                .apply()
            if (isRememberPasswordEnabled()) {
                credentialStore.saveLocalPassword(newPassword)
            }
            publishState()
        }
    }

    /** 仅校验密码是否能解开当前验证器。不修改解锁状态。 */
    fun verifyPassword(password: String): Boolean {
        val raw = prefs.getString(KEY_VERIFIER, null) ?: return false
        val bytes = runCatching { Base64.decode(raw, Base64.NO_WRAP) }.getOrNull() ?: return false
        val salt = credentialStore.loadOrCreateCloudCipherSalt()
        val kek = CryptoBox.deriveKey(password, salt)
        return runCatching { unwrapDek(kek, bytes) }.isSuccess
    }

    private fun isPasswordConfigured(): Boolean = prefs.contains(KEY_VERIFIER)

    private fun computeState(): AppLockState = when {
        !isPasswordConfigured() -> AppLockState.NeedSetup
        unlockedFlag -> AppLockState.Unlocked
        else -> AppLockState.Locked
    }

    private fun publishState() {
        _state.value = computeState()
    }

    /**
     * 验证器格式：`[1B 版本=1][12B IV][密文+16B GCM tag]`。
     * 使用直接调用 Cipher 而非 [CryptoBox.encryptToBase64]，避免引入 Base64 双层封装。
     */
    private fun wrapDek(kek: SecretKey, dek: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(dek)
        return ByteArray(1 + iv.size + ct.size).also { out ->
            out[0] = VERSION
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
        }
    }

    private fun unwrapDek(kek: SecretKey, raw: ByteArray): ByteArray {
        require(raw.isNotEmpty() && raw[0] == VERSION) { "未知的验证器版本" }
        require(raw.size > 1 + IV_BYTES) { "验证器长度不足" }
        val iv = raw.copyOfRange(1, 1 + IV_BYTES)
        val ct = raw.copyOfRange(1 + IV_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private companion object {
        const val PREF_FILE_NAME = "tracediary_app_password_prefs"
        const val KEY_VERIFIER = "password_verifier"
        const val KEY_REMEMBER_PASSWORD = "remember_password"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
        // SecretKeySpec 不需要在这里手动构造，预留以备将来 PBKDF2 参数微调。
        @Suppress("UNUSED")
        fun keyOf(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")
    }
}

/** 应用解锁状态。 */
enum class AppLockState {
    NeedSetup,
    Locked,
    Unlocked,
}
