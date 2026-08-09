package com.tracediary.app.data.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用 Android Keystore 与 EncryptedSharedPreferences 验证真实应用密码生命周期。 */
@RunWith(AndroidJUnit4::class)
class AppPasswordManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var preferenceFileName: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferenceFileName = "app-password-test-${System.nanoTime()}"
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(preferenceFileName)
    }

    @Test
    fun setupLockWrongPasswordAndUnlock_followExpectedStates() {
        val credentialStore = InMemoryCredentialStore()
        val acceptedPasswords = mutableListOf<String>()
        val manager = newManager(credentialStore) { acceptedPasswords += it }

        assertEquals(AppLockState.NeedSetup, manager.state.value)
        assertTrue(manager.setupInitialPassword("正确密码", remember = false).isSuccess)
        assertEquals(AppLockState.Unlocked, manager.state.value)
        assertEquals(listOf("正确密码"), acceptedPasswords)
        assertNull(credentialStore.loadLocalPassword())

        manager.lockNow()
        assertEquals(AppLockState.Locked, manager.state.value)
        assertTrue(manager.unlock("错误密码").isFailure)
        assertEquals(AppLockState.Locked, manager.state.value)
        assertTrue(manager.unlock("正确密码").isSuccess)
        assertEquals(AppLockState.Unlocked, manager.state.value)
    }

    @Test
    fun rememberedPassword_autoUnlocksNewManagerInstance() {
        val credentialStore = InMemoryCredentialStore()
        val first = newManager(credentialStore)

        assertTrue(first.setupInitialPassword("记住的密码", remember = true).isSuccess)
        assertEquals("记住的密码", credentialStore.loadLocalPassword())

        val second = newManager(credentialStore)
        assertEquals(AppLockState.Unlocked, second.state.value)
        assertEquals("记住的密码", second.loadActivePassword())

        second.setRememberPasswordEnabled(false)
        assertFalse(second.isRememberPasswordEnabled())
        assertNull(credentialStore.loadLocalPassword())
    }

    private fun newManager(
        credentialStore: InMemoryCredentialStore,
        onPasswordAccepted: (String) -> Unit = {},
    ): AppPasswordManager = AppPasswordManager(
        context = context,
        credentialStore = credentialStore,
        onPasswordAccepted = onPasswordAccepted,
        preferenceFileName = preferenceFileName,
    )

    private class InMemoryCredentialStore : CloudCipherCredentialStore {
        private var password: String? = null

        override fun saveLocalPassword(value: String?) {
            password = value
        }

        override fun loadLocalPassword(): String? = password
    }
}
