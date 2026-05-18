package com.tracediary.app.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoBoxTest {

    @Test
    fun encryptToBase64_samePlaintextProducesDifferentCiphertext() {
        val key = CryptoBox.deriveKey("password", "salt-123456789012".toByteArray())
        val plaintext = "同步内容".toByteArray(Charsets.UTF_8)

        val first = CryptoBox.encryptToBase64(key, plaintext)
        val second = CryptoBox.encryptToBase64(key, plaintext)

        assertNotEquals(first, second)
        assertArrayEquals(plaintext, CryptoBox.decryptFromBase64(key, first))
        assertArrayEquals(plaintext, CryptoBox.decryptFromBase64(key, second))
    }

    @Test
    fun decryptFromBase64_rejectsWrongPasswordOrSalt() {
        val plaintext = "同步内容".toByteArray(Charsets.UTF_8)
        val correctKey = CryptoBox.deriveKey("password", "salt-123456789012".toByteArray())
        val wrongKey = CryptoBox.deriveKey("password", "other-salt-12345".toByteArray())
        val cipher = CryptoBox.encryptToBase64(correctKey, plaintext)

        assertThrows(Throwable::class.java) {
            CryptoBox.decryptFromBase64(wrongKey, cipher)
        }
    }

    @Test
    fun decryptFromBase64_rejectsIllegalVersionByte() {
        val key = CryptoBox.deriveKey("password", "salt-123456789012".toByteArray())
        val cipher = CryptoBox.encryptToBase64(key, "同步内容".toByteArray(Charsets.UTF_8))
        val raw = java.util.Base64.getDecoder().decode(cipher)
        raw[0] = 99
        val corrupted = java.util.Base64.getEncoder().encodeToString(raw)

        assertThrows(IllegalArgumentException::class.java) {
            CryptoBox.decryptFromBase64(key, corrupted)
        }
    }
}
