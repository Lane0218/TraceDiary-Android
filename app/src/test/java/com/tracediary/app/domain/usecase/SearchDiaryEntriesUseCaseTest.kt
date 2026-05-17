package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDiaryEntriesUseCaseTest {
    private val useCase = SearchDiaryEntriesUseCase()

    @Test
    fun search_filtersByKeywordYearAndRecordedDatesDescending() {
        val entries = listOf(
            entry("2025-05-17", "散步和读书"),
            entry("2026-05-15", "午后散步"),
            entry("2026-05-16", "工作复盘"),
            entry("2026-05-17", "继续散步"),
        )

        val result = useCase.search(
            entries = entries,
            query = DiarySearchQuery(
                keyword = "散步",
                year = 2026,
                recordedDates = setOf(LocalDate.parse("2026-05-15"), LocalDate.parse("2026-05-17")),
            ),
        )

        assertEquals(
            listOf(LocalDate.parse("2026-05-17"), LocalDate.parse("2026-05-15")),
            result.map(DiaryEntry::date),
        )
    }

    @Test
    fun search_canReturnBlankEntriesWhenOnlyRecordedIsFalse() {
        val entries = listOf(
            entry("2026-05-16", "   "),
            entry("2026-05-17", "有内容"),
        )

        val result = useCase.search(entries, DiarySearchQuery(onlyRecorded = false, sortOrder = DiarySortOrder.Ascending))

        assertEquals(
            listOf(LocalDate.parse("2026-05-16"), LocalDate.parse("2026-05-17")),
            result.map(DiaryEntry::date),
        )
    }

    @Test
    fun search_isCaseInsensitiveForLatinText() {
        val entries = listOf(
            entry("2026-05-17", "Kotlin domain logic"),
            entry("2026-05-18", "Compose UI"),
        )

        val result = useCase.search(entries, DiarySearchQuery(keyword = "kotlin"))

        assertEquals(listOf(LocalDate.parse("2026-05-17")), result.map(DiaryEntry::date))
    }

    private fun entry(date: String, content: String): DiaryEntry =
        DiaryEntry(
            date = LocalDate.parse(date),
            content = content,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-17T00:00:00Z"),
            syncState = SyncState.Synced,
        )
}
