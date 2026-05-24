package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.data.security.CryptoBox
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import javax.crypto.SecretKey

class SyncSerializationTest {

    private val key: SecretKey = CryptoBox.deriveCloudKey("sync-password")

    @Test
    fun encodeThenDecodeDiary_isLossless() {
        val entry = DiaryEntry(
            date = LocalDate.of(2025, 3, 14),
            content = "今天写了同步引擎单测。\n第二行内容。",
            createdAt = Instant.parse("2025-03-14T01:02:03Z"),
            updatedAt = Instant.parse("2025-03-14T05:06:07Z"),
            syncState = SyncState.LocalOnly,
        )

        val encoded = encodeDiary(entry, key)
        val decoded = decodeDiary(encoded, key)

        assertEquals(entry.date, decoded.date)
        assertEquals(entry.content, decoded.content)
        assertEquals(entry.createdAt, decoded.createdAt)
        assertEquals(entry.updatedAt, decoded.updatedAt)
        // toDomain 强制 syncState=Synced，这里不参与对比
        val json = encoded.toString(Charsets.UTF_8)
        assertTrue(json.contains("\"cipher\""))
        assertFalse(json.contains("\"v\""))
        assertFalse(json.contains("\"salt\""))
        assertFalse(json.contains(entry.content))
    }

    @Test
    fun encodeThenDecodeAnnual_isLossless() {
        val summary = AnnualSummary(
            year = 2025,
            content = "年度总结正文",
            createdAt = Instant.parse("2025-12-31T00:00:00Z"),
            updatedAt = Instant.parse("2025-12-31T23:59:59Z"),
            syncState = SyncState.Pending,
        )

        val encoded = encodeAnnual(summary, key)
        val decoded = decodeAnnual(encoded, key)

        assertEquals(summary.year, decoded.year)
        assertEquals(summary.content, decoded.content)
        assertEquals(summary.createdAt, decoded.createdAt)
        assertEquals(summary.updatedAt, decoded.updatedAt)
    }

    @Test
    fun decodeDiary_rejectsLegacyPlaintext() {
        val json = """
            {
              "date": "2025-01-02",
              "content": "hello",
              "createdAt": "2025-01-02T03:04:05Z",
              "updatedAt": "2025-01-02T03:04:06Z",
              "schemaVersion": 1,
              "futureField": "noop",
              "anotherFuture": 42
            }
        """.trimIndent()

        assertThrows(Throwable::class.java) {
            decodeDiary(json.toByteArray(Charsets.UTF_8), key)
        }
    }

    @Test
    fun decodeAnnual_rejectsLegacyPlaintext() {
        val json = """
            {
              "year": 2025,
              "content": "y",
              "createdAt": "2025-01-01T00:00:00Z",
              "updatedAt": "2025-12-31T00:00:00Z",
              "schemaVersion": 1,
              "extension": {"k":"v"}
            }
        """.trimIndent()

        assertThrows(Throwable::class.java) {
            decodeAnnual(json.toByteArray(Charsets.UTF_8), key)
        }
    }

    @Test
    fun decodeDiary_rejectsCorruptedCipher() {
        val envelope = DiaryEnvelope(
            date = "2025-01-02",
            updatedAt = "2025-01-02T03:04:06Z",
            cipher = "not-valid-base64",
        )
        val bytes = syncJson.encodeToString(envelope).toByteArray(Charsets.UTF_8)

        assertThrows(Throwable::class.java) {
            decodeDiary(bytes, key)
        }
    }

    @Test
    fun decodeDiary_rejectsWrongPassword() {
        val entry = DiaryEntry(
            date = LocalDate.of(2025, 3, 14),
            content = "需要正确应用密码才能解密",
            createdAt = Instant.parse("2025-03-14T01:02:03Z"),
            updatedAt = Instant.parse("2025-03-14T05:06:07Z"),
            syncState = SyncState.LocalOnly,
        )
        val encoded = encodeDiary(entry, key)
        val wrongKey = CryptoBox.deriveCloudKey("wrong-password")

        assertThrows(Throwable::class.java) {
            decodeDiary(encoded, wrongKey)
        }
    }
}
