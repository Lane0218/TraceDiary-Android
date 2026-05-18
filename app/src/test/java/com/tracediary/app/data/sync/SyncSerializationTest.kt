package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncSerializationTest {

    @Test
    fun encodeThenDecodeDiary_isLossless() {
        val entry = DiaryEntry(
            date = LocalDate.of(2025, 3, 14),
            content = "今天写了同步引擎单测。\n第二行内容。",
            createdAt = Instant.parse("2025-03-14T01:02:03Z"),
            updatedAt = Instant.parse("2025-03-14T05:06:07Z"),
            syncState = SyncState.LocalOnly,
        )

        val decoded = decodeDiary(encodeDiary(entry))

        assertEquals(entry.date, decoded.date)
        assertEquals(entry.content, decoded.content)
        assertEquals(entry.createdAt, decoded.createdAt)
        assertEquals(entry.updatedAt, decoded.updatedAt)
        // toDomain 强制 syncState=Synced，这里不参与对比
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

        val decoded = decodeAnnual(encodeAnnual(summary))

        assertEquals(summary.year, decoded.year)
        assertEquals(summary.content, decoded.content)
        assertEquals(summary.createdAt, decoded.createdAt)
        assertEquals(summary.updatedAt, decoded.updatedAt)
    }

    @Test
    fun decodeDiary_ignoresUnknownFields() {
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

        val decoded = decodeDiary(json.toByteArray(Charsets.UTF_8))

        assertEquals(LocalDate.of(2025, 1, 2), decoded.date)
        assertEquals("hello", decoded.content)
        assertEquals(Instant.parse("2025-01-02T03:04:06Z"), decoded.updatedAt)
    }

    @Test
    fun decodeAnnual_ignoresUnknownFields() {
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

        val decoded = decodeAnnual(json.toByteArray(Charsets.UTF_8))

        assertEquals(2025, decoded.year)
        assertEquals("y", decoded.content)
    }
}
