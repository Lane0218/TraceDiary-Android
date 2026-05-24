package com.tracediary.app.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoBoxTest {

    @Test
    fun encryptToBase64_samePlaintextProducesDifferentCiphertext() {
        val key = CryptoBox.deriveCloudKey("password")
        val plaintext = "同步内容".toByteArray(Charsets.UTF_8)

        val first = CryptoBox.encryptToBase64(key, plaintext)
        val second = CryptoBox.encryptToBase64(key, plaintext)

        assertNotEquals(first, second)
        assertArrayEquals(plaintext, CryptoBox.decryptFromBase64(key, first))
        assertArrayEquals(plaintext, CryptoBox.decryptFromBase64(key, second))
    }

    @Test
    fun encryptToBase64_outputIsIvFollowedByCiphertextAndTag() {
        val key = CryptoBox.deriveCloudKey("password")
        val plaintext = "abc".toByteArray(Charsets.UTF_8)

        val cipher = CryptoBox.encryptToBase64(key, plaintext)
        val raw = java.util.Base64.getDecoder().decode(cipher)

        assertEquals(12 + plaintext.size + 16, raw.size)
    }

    @Test
    fun decryptFromBase64_rejectsWrongPassword() {
        val plaintext = "同步内容".toByteArray(Charsets.UTF_8)
        val correctKey = CryptoBox.deriveCloudKey("password")
        val wrongKey = CryptoBox.deriveCloudKey("wrong-password")
        val cipher = CryptoBox.encryptToBase64(correctKey, plaintext)

        assertThrows(Throwable::class.java) {
            CryptoBox.decryptFromBase64(wrongKey, cipher)
        }
    }

    @Test
    fun decryptFromBase64_rejectsTamperedIv() {
        val key = CryptoBox.deriveCloudKey("password")
        val cipher = CryptoBox.encryptToBase64(key, "同步内容".toByteArray(Charsets.UTF_8))
        val raw = java.util.Base64.getDecoder().decode(cipher)
        raw[0] = 99
        val corrupted = java.util.Base64.getEncoder().encodeToString(raw)

        assertThrows(Throwable::class.java) {
            CryptoBox.decryptFromBase64(key, corrupted)
        }
    }

    @Test
    fun deriveDatabasePassphrase_isStableForSamePasswordAndDifferentForDifferentPassword() {
        val first = CryptoBox.deriveDatabasePassphrase("password")
        val second = CryptoBox.deriveDatabasePassphrase("password")
        val other = CryptoBox.deriveDatabasePassphrase("other-password")
        val firstText = first.toString(Charsets.UTF_8)

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(other))
        assertTrue(firstText.startsWith("td-db-v3:"))
        assertFalse(firstText.contains("password"))
    }

    @Test
    fun deriveKeys_areIsolatedByPurpose() {
        val password = "same-application-password"
        val cloudKey = CryptoBox.deriveCloudKey(password).encoded
        val verifierKey = CryptoBox.derivePasswordVerifierKey(password).encoded
        val databasePassphrase = CryptoBox.deriveDatabasePassphrase(password)

        assertFalse(cloudKey.contentEquals(verifierKey))
        assertFalse(databasePassphrase.contentEquals(cloudKey))
        assertFalse(databasePassphrase.contentEquals(verifierKey))
    }
}
