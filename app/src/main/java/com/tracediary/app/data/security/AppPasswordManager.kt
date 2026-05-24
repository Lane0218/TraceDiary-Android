package com.tracediary.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用密码 / 数据密钥管理。
 *
 * 模型：
 * - 唯一秘密是用户应用密码；数据库、云端与验证器分别用固定公开 salt 派生 key。
 * - 验证器是固定明文载荷的 AES-GCM 密文，只用于判断密码是否正确，不包含随机 DEK。
 * - “记住密码”只把应用密码缓存在本机 EncryptedSharedPreferences 中，关闭后不会影响
 *   本地数据库或云端对象在其它设备上的可恢复性。
 *
 * 状态：
 * - [AppLockState.NeedSetup]：未设置应用密码（首次启动 / 升级清库）。
 * - [AppLockState.Locked]：已设置但当前未解锁。
 * - [AppLockState.Unlocked]：已解锁，UI 可正常进入。
 */
class AppPasswordManager(
    context: Context,
    private val credentialStore: SecureCredentialStore,
    private val onPasswordAccepted: (String) -> Unit = {},
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

    /**
     * 当前已解锁的应用密码。仅在内存中存活：
     * - "记住密码"开启时与 [SecureCredentialStore] 同步；
     * - "记住密码"关闭时不写入磁盘，但只要本次解锁未失效，SyncEngine / 自动同步仍能读到。
     */
    @Volatile private var activePasswordRef: String? = null

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    init {
        // 启动时若已记住密码，可自动解锁；解锁失败保持 Locked，等待用户输入。
        if (isPasswordConfigured() && isRememberPasswordEnabled()) {
            credentialStore.loadLocalPassword()?.let { stored ->
                runCatching { verifyPassword(stored) }
                    .onSuccess { ok ->
                        if (ok) {
                            onPasswordAccepted(stored)
                            unlockedFlag = true
                            activePasswordRef = stored
                        }
                    }
            }
        }
        publishState()
    }

    fun isUnlocked(): Boolean = unlockedFlag

    fun needsSetup(): Boolean = !isPasswordConfigured()

    /**
     * 获取当前激活的应用密码。仅当已解锁时返回非空；
     * 优先返回内存中的 [activePasswordRef]，回退到 [SecureCredentialStore]。
     */
    fun loadActivePassword(): String? {
        if (!unlockedFlag) return null
        return activePasswordRef ?: credentialStore.loadLocalPassword()
    }

    fun isRememberPasswordEnabled(): Boolean =
        prefs.getBoolean(KEY_REMEMBER_PASSWORD, true)

    /** 首次设置应用密码：生成验证器并按需记住密码。返回 true 表示已成功落库，false 表示密码空。 */
    fun setupInitialPassword(password: String, remember: Boolean): Result<Unit> {
        if (password.isEmpty()) return Result.failure(IllegalArgumentException("密码不能为空"))
        if (isPasswordConfigured()) return Result.failure(IllegalStateException("已设置应用密码，请使用修改密码"))
        return runCatching {
            val verifier = createVerifier(password)
            prefs.edit()
                .putString(KEY_VERIFIER, verifier)
                .putInt(KEY_VERIFIER_VERSION, CURRENT_VERIFIER_VERSION)
                .putBoolean(KEY_REMEMBER_PASSWORD, remember)
                .apply()
            credentialStore.saveLocalPassword(if (remember) password else null)
            onPasswordAccepted(password)
            unlockedFlag = true
            activePasswordRef = password
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
            onPasswordAccepted(password)
            unlockedFlag = true
            activePasswordRef = password
            // 解锁成功且当前是"记住密码"配置，把内存中的密码同步到 SecureCredentialStore，
            // 让 SyncEngine 等同步组件能用同一个密码加密云端对象。
            if (isRememberPasswordEnabled()) {
                credentialStore.saveLocalPassword(password)
            }
            publishState()
        }
    }

    /** 立即上锁。内存态 [unlockedFlag] 与 [activePasswordRef] 一并清空；持久化的验证器不变。 */
    fun lockNow() {
        unlockedFlag = false
        activePasswordRef = null
        publishState()
    }

    /** 切换"在本机记住密码"。关闭时立即抹掉 SecureCredentialStore 里的密码缓存。 */
    fun setRememberPasswordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_PASSWORD, enabled).apply()
        if (!enabled) credentialStore.saveLocalPassword(null)
    }

    /**
     * 修改应用密码：只更新本地验证器与便利缓存。调用方必须先完成 SQLCipher rekey
     * 与云端对象 rekey，确认成功后再调用本方法。
     */
    fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        if (newPassword.isEmpty()) return Result.failure(IllegalArgumentException("新密码不能为空"))
        if (!verifyPassword(oldPassword)) return Result.failure(IllegalArgumentException("旧密码错误"))
        return runCatching {
            val verifier = createVerifier(newPassword)
            prefs.edit()
                .putString(KEY_VERIFIER, verifier)
                .putInt(KEY_VERIFIER_VERSION, CURRENT_VERIFIER_VERSION)
                .apply()
            if (isRememberPasswordEnabled()) {
                credentialStore.saveLocalPassword(newPassword)
            }
            activePasswordRef = newPassword
            publishState()
        }
    }

    /** 仅校验密码是否能解开当前验证器。不修改解锁状态。 */
    fun verifyPassword(password: String): Boolean {
        if (!isPasswordConfigured()) return false
        val raw = prefs.getString(KEY_VERIFIER, null) ?: return false
        val key = CryptoBox.derivePasswordVerifierKey(password)
        val payload = runCatching { CryptoBox.decryptFromBase64(key, raw) }.getOrNull() ?: return false
        return payload.contentEquals(CryptoBox.verifierPayload())
    }

    private fun isPasswordConfigured(): Boolean =
        prefs.contains(KEY_VERIFIER) &&
            prefs.getInt(KEY_VERIFIER_VERSION, 0) == CURRENT_VERIFIER_VERSION

    private fun computeState(): AppLockState = when {
        !isPasswordConfigured() -> AppLockState.NeedSetup
        unlockedFlag -> AppLockState.Unlocked
        else -> AppLockState.Locked
    }

    private fun publishState() {
        _state.value = computeState()
    }

    private fun createVerifier(password: String): String =
        CryptoBox.encryptToBase64(
            key = CryptoBox.derivePasswordVerifierKey(password),
            plaintext = CryptoBox.verifierPayload(),
        )

    private companion object {
        const val PREF_FILE_NAME = "tracediary_app_password_prefs"
        const val KEY_VERIFIER = "password_verifier"
        const val KEY_VERIFIER_VERSION = "password_verifier_version"
        const val KEY_REMEMBER_PASSWORD = "remember_password"
        const val CURRENT_VERIFIER_VERSION = 2
    }
}

/** 应用解锁状态。 */
enum class AppLockState {
    NeedSetup,
    Locked,
    Unlocked,
}
