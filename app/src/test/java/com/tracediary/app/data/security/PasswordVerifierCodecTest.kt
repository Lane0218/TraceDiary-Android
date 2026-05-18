package com.tracediary.app.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class PasswordVerifierCodecTest {

    private val salt = "salt-for-test-16".toByteArray()
    private val dek = "tracediary-fake-data-encryption-key".toByteArray()

    @Test
    fun wrapAndUnwrapRoundtrip() {
        val kek = CryptoBox.deriveKey("correct-password", salt)
        val wrapped = PasswordVerifierCodec.wrap(kek, dek)

        val unwrapped = PasswordVerifierCodec.unwrap(kek, wrapped)
        assertArrayEquals(dek, unwrapped)
    }

    @Test
    fun wrappedBlobHasVersionByteAndIv() {
        val kek = CryptoBox.deriveKey("any-password", salt)
        val wrapped = PasswordVerifierCodec.wrap(kek, dek)

        // 第一个字节是版本号 1，后面 12 字节是 IV。
        assertTrue(wrapped.size > 1 + PasswordVerifierCodec.IV_BYTES + dek.size)
        assertTrue(wrapped[0] == PasswordVerifierCodec.VERSION)
    }

    @Test
    fun unwrapWithWrongPasswordFails() {
        val wrapped = PasswordVerifierCodec.wrap(CryptoBox.deriveKey("correct", salt), dek)
        val wrongKek = CryptoBox.deriveKey("wrong", salt)

        try {
            PasswordVerifierCodec.unwrap(wrongKek, wrapped)
            fail("Expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // 预期：GCM tag 校验失败 → 错误密码。
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unwrapWithInvalidVersionThrows() {
        val kek = CryptoBox.deriveKey("p", salt)
        val tampered = ByteArray(20)
        tampered[0] = 0x09 // 非法版本号
        PasswordVerifierCodec.unwrap(kek, tampered)
    }
}
