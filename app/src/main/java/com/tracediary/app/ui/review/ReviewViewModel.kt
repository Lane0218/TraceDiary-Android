package com.tracediary.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.DiarySearchQuery
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.OptIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * 回顾页（搜索 / AI问答 / 年度总结）共享的 ViewModel。
 *
 * 设计要点：
 * - 单一 ViewModel，两个子 UI 状态。
 * - 通过 [DiaryRepository.observeAllEntries] 拿到的全量数据驱动搜索可用年份。
 */
class ReviewViewModel(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    @Suppress("UNUSED_PARAMETER") calendarUseCase: DiaryCalendarUseCase,
    private val searchUseCase: SearchDiaryEntriesUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val initialDate: LocalDate = LocalDate.now(clock),
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    private val _state = MutableStateFlow(ReviewUiState.initial())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** 全量 entries，驱动搜索结果与可用年份。 */
    private var allEntries: List<DiaryEntry> = emptyList()

    private val keywordFlow = MutableStateFlow(_state.value.search.keyword)

    private var summarySaveJob: Job? = null

    init {
        observeAllEntries()
        observeAnnualSummaries()
        observeSearchKeyword()
    }

    private fun observeAllEntries() {
        workScope.launch {
            diaryRepository.observeAllEntries().collectLatest { entries ->
                allEntries = entries
                val years = entries.asSequence()
                    .map { it.date.year }
                    .distinct()
                    .sortedDescending()
                    .toList()
                _state.update { current ->
                    current.copy(
                        search = current.search.copy(
                            availableYears = years,
                            appliedKeyword = current.search.keyword.trim(),
                            results = runSearch(entries, current.search),
                        ),
                    )
                }
            }
        }
    }

    private fun observeAnnualSummaries() {
        workScope.launch {
            annualSummaryRepository.observeSummaries().collectLatest { summaries ->
                val ordered = summaries.sortedByDescending(AnnualSummary::year)
                val existingYears = ordered.map(AnnualSummary::year).toSet()
                val currentYear = initialDate.year
                val writableYears = (0 until WRITABLE_YEAR_WINDOW)
                    .map { offset -> currentYear - offset }
                    .filter { year -> year !in existingYears }
                _state.update { current ->
                    val selectedYear = current.annual.selectedYear
                    val refreshedContent = if (selectedYear != null) {
                        ordered.firstOrNull { it.year == selectedYear }?.content
                            ?: current.annual.editingContent
                    } else {
                        current.annual.editingContent
                    }
                    current.copy(
                        annual = current.annual.copy(
                            summaries = ordered,
                            writableYears = writableYears,
                            editingContent = refreshedContent,
                        ),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearchKeyword() {
        workScope.launch {
            keywordFlow
                .debounceCompat(KEYWORD_DEBOUNCE_MS)
                .collectLatest { keyword ->
                    _state.update { current ->
                        val updatedSearch = current.search.copy(keyword = keyword)
                        current.copy(
                            search = updatedSearch.copy(
                                appliedKeyword = keyword.trim(),
                                results = runSearch(allEntries, updatedSearch),
                            ),
                        )
                    }
                }
        }
    }

    fun onSelectTab(tab: ReviewTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onOpenSummary(year: Int) {
        summarySaveJob?.cancel()
        val existing = _state.value.annual.summaries.firstOrNull { it.year == year }
        _state.update { current ->
            current.copy(
                annual = current.annual.copy(
                    selectedYear = year,
                    editingContent = existing?.content.orEmpty(),
                    editingSaveStatus = SaveStatus.Idle,
                ),
            )
        }
    }

    fun onCloseSummary() {
        summarySaveJob?.cancel()
        summarySaveJob = null
        _state.update { current ->
            current.copy(
                annual = current.annual.copy(
                    selectedYear = null,
                    editingContent = "",
                    editingSaveStatus = SaveStatus.Idle,
                ),
            )
        }
    }

    fun onDeleteSummary() {
        val year = _state.value.annual.selectedYear ?: return
        summarySaveJob?.cancel()
        summarySaveJob = null
        workScope.launch {
            annualSummaryRepository.deleteSummary(year)
            _state.update { current ->
                current.copy(
                    annual = current.annual.copy(
                        selectedYear = null,
                        editingContent = "",
                        editingSaveStatus = SaveStatus.Idle,
                    ),
                )
            }
        }
    }

    fun onSummaryContentChanged(text: String) {
        val year = _state.value.annual.selectedYear ?: return
        _state.update { current ->
            current.copy(
                annual = current.annual.copy(
                    editingContent = text,
                    editingSaveStatus = SaveStatus.Saving,
                ),
            )
        }
        summarySaveJob?.cancel()
        summarySaveJob = workScope.launch {
            delay(SUMMARY_SAVE_DEBOUNCE_MS)
            try {
                annualSummaryRepository.saveSummary(year, text)
                _state.update { current ->
                    if (current.annual.selectedYear == year) {
                        current.copy(
                            annual = current.annual.copy(editingSaveStatus = SaveStatus.Saved),
                        )
                    } else current
                }
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.annual.selectedYear == year) {
                        current.copy(
                            annual = current.annual.copy(editingSaveStatus = SaveStatus.Failed),
                        )
                    } else current
                }
            }
        }
    }

    fun onKeywordChanged(text: String) {
        _state.update { current ->
            current.copy(search = current.search.copy(keyword = text))
        }
        keywordFlow.value = text
    }

    fun onYearFilterChanged(year: Int?) {
        _state.update { current ->
            val updated = current.search.copy(yearFilter = year)
            current.copy(
                search = updated.copy(
                    appliedKeyword = updated.keyword.trim(),
                    results = runSearch(allEntries, updated),
                ),
            )
        }
    }

    fun onToggleRecordedOnly(value: Boolean) {
        _state.update { current ->
            val updated = current.search.copy(recordedOnly = value)
            current.copy(
                search = updated.copy(
                    appliedKeyword = updated.keyword.trim(),
                    results = runSearch(allEntries, updated),
                ),
            )
        }
    }

    private fun runSearch(entries: List<DiaryEntry>, search: SearchUiState): List<DiaryEntry> {
        return searchUseCase.search(
            entries,
            DiarySearchQuery(
                keyword = search.keyword,
                year = search.yearFilter,
                onlyRecorded = search.recordedOnly,
            ),
        )
    }

    private inline fun MutableStateFlow<ReviewUiState>.update(transform: (ReviewUiState) -> ReviewUiState) {
        value = transform(value)
    }

    companion object {
        const val KEYWORD_DEBOUNCE_MS: Long = 300L
        const val SUMMARY_SAVE_DEBOUNCE_MS: Long = 600L
        /** 年度总结新建入口默认覆盖：今年起向前 5 年。 */
        const val WRITABLE_YEAR_WINDOW: Int = 5

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReviewViewModel(
                        diaryRepository = application.diaryRepository,
                        annualSummaryRepository = application.annualSummaryRepository,
                        calendarUseCase = application.calendarUseCase,
                        searchUseCase = application.searchUseCase,
                        initialDate = application.currentAppDate(),
                    )
                }
            }
}
}

/**
 * 自定义 debounce：避免直接依赖 [kotlinx.coroutines.flow.debounce]
 * 在某些 Kotlin/coroutines 版本上仍标注为 FlowPreview 的稳定性问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> kotlinx.coroutines.flow.Flow<T>.debounceCompat(timeoutMillis: Long): kotlinx.coroutines.flow.Flow<T> =
    kotlinx.coroutines.flow.channelFlow {
        var pending: Job? = null
        collect { value ->
            pending?.cancel()
            pending = launch {
                delay(timeoutMillis)
                send(value)
            }
        }
    }

enum class ReviewTab { Search, Ai, AnnualSummary }

enum class SaveStatus { Idle, Saving, Saved, Failed }

data class AnnualUiState(
    val summaries: List<AnnualSummary> = emptyList(),
    val writableYears: List<Int> = emptyList(),
    val selectedYear: Int? = null,
    val editingContent: String = "",
    val editingSaveStatus: SaveStatus = SaveStatus.Idle,
)

data class SearchUiState(
    val keyword: String = "",
    val appliedKeyword: String = "",
    val yearFilter: Int? = null,
    val recordedOnly: Boolean = false,
    val results: List<DiaryEntry> = emptyList(),
    val availableYears: List<Int> = emptyList(),
)

data class ReviewUiState(
    val selectedTab: ReviewTab,
    val annual: AnnualUiState,
    val search: SearchUiState,
) {
    companion object {
        fun initial(): ReviewUiState = ReviewUiState(
            selectedTab = ReviewTab.Search,
            annual = AnnualUiState(),
            search = SearchUiState(),
        )
    }
}
