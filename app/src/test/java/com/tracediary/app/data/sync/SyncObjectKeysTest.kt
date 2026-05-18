package com.tracediary.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SyncObjectKeysTest {

    @Test
    fun diaryObjectKey_padsMonthAndDay() {
        val key = SyncObjectKeys.diaryObjectKey(LocalDate.of(2025, 1, 2))
        assertEquals("diary/2025-01-02.json", key)
    }

    @Test
    fun diaryObjectKey_keepsTwoDigitMonthAndDay() {
        val key = SyncObjectKeys.diaryObjectKey(LocalDate.of(2025, 12, 31))
        assertEquals("diary/2025-12-31.json", key)
    }

    @Test
    fun annualObjectKey_formatsYear() {
        assertEquals("annual/2025.json", SyncObjectKeys.annualObjectKey(2025))
    }

    @Test
    fun parseDiaryKey_acceptsPlainKey() {
        assertEquals(LocalDate.of(2025, 1, 2), SyncObjectKeys.parseDiaryKey("diary/2025-01-02.json"))
    }

    @Test
    fun parseDiaryKey_acceptsKeyWithBucketPrefix() {
        assertEquals(
            LocalDate.of(2025, 1, 2),
            SyncObjectKeys.parseDiaryKey("TraceDiary/diary/2025-01-02.json"),
        )
    }

    @Test
    fun parseDiaryKey_rejectsUnpaddedDate() {
        assertNull(SyncObjectKeys.parseDiaryKey("diary/2025-1-2.json"))
    }

    @Test
    fun parseDiaryKey_rejectsWrongPrefix() {
        assertNull(SyncObjectKeys.parseDiaryKey("other/2025-01-02.json"))
    }

    @Test
    fun parseDiaryKey_rejectsEmpty() {
        assertNull(SyncObjectKeys.parseDiaryKey(""))
    }

    @Test
    fun parseDiaryKey_rejectsBlank() {
        assertNull(SyncObjectKeys.parseDiaryKey("   "))
    }

    @Test
    fun parseAnnualKey_acceptsPlainKey() {
        assertEquals(2025, SyncObjectKeys.parseAnnualKey("annual/2025.json"))
    }

    @Test
    fun parseAnnualKey_acceptsKeyWithBucketPrefix() {
        assertEquals(2025, SyncObjectKeys.parseAnnualKey("TraceDiary/annual/2025.json"))
    }

    @Test
    fun parseAnnualKey_rejectsThreeDigitYear() {
        assertNull(SyncObjectKeys.parseAnnualKey("annual/202.json"))
    }

    @Test
    fun parseAnnualKey_rejectsWrongPrefix() {
        assertNull(SyncObjectKeys.parseAnnualKey("other/2025.json"))
    }

    @Test
    fun parseAnnualKey_rejectsEmpty() {
        assertNull(SyncObjectKeys.parseAnnualKey(""))
    }

    @Test
    fun parseAnnualKey_rejectsBlank() {
        assertNull(SyncObjectKeys.parseAnnualKey("   "))
    }
}
