package com.tracediary.app.domain.usecase

import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.isRecorded
import java.time.LocalDate

data class DiarySearchQuery(
    val keyword: String? = null,
    val year: Int? = null,
    val recordedDates: Set<LocalDate>? = null,
    val onlyRecorded: Boolean = true,
    val sortOrder: DiarySortOrder = DiarySortOrder.Descending,
)

enum class DiarySortOrder {
    Ascending,
    Descending,
}

class SearchDiaryEntriesUseCase {
    fun search(entries: List<DiaryEntry>, query: DiarySearchQuery = DiarySearchQuery()): List<DiaryEntry> {
        val keyword = query.keyword?.trim().orEmpty()

        return entries.latestEntryByDate().values
            .asSequence()
            .filter { entry -> !query.onlyRecorded || entry.isRecorded }
            .filter { entry -> query.year == null || entry.date.year == query.year }
            .filter { entry -> query.recordedDates == null || entry.date in query.recordedDates }
            .filter { entry -> keyword.isEmpty() || entry.content.contains(keyword, ignoreCase = true) }
            .sortedWith(query.sortOrder.comparator)
            .toList()
    }

    private val DiarySortOrder.comparator: Comparator<DiaryEntry>
        get() = when (this) {
            DiarySortOrder.Ascending -> compareBy(DiaryEntry::date)
            DiarySortOrder.Descending -> compareByDescending(DiaryEntry::date)
        }
}
