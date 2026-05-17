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
import com.tracediary.app.domain.usecase.CalendarDay
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlin.OptIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * 回顾页（日历 / 年度总结 / 搜索）共享的 ViewModel。
 *
 * 设计要点：
 * - 单一 ViewModel，三个子 UI 状态，便于切 Tab 时复用同一份 entries 数据。
 * - 通过 [DiaryRepository.observeAllEntries] 拿到的全量数据驱动“往年今日”和搜索。
 * - 月份网格依据 [displayMonth] 通过 [DiaryRepository.observeEntriesInMonth] 实时拉取。
 */
class ReviewViewModel(
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val calendarUseCase: DiaryCalendarUseCase,
    private val searchUseCase: SearchDiaryEntriesUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    private val _state = MutableStateFlow(ReviewUiState.initial(LocalDate.now(clock)))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** 全量 entries，驱动“往年今日”、搜索、可用年份等。 */
    private var allEntries: List<DiaryEntry> = emptyList()

    /** 当前展示月份对应的 entries，用于月网格“是否有记录”标记。 */
    private val displayMonthFlow: MutableStateFlow<YearMonth> =
        MutableStateFlow(_state.value.calendar.displayMonth)

    private val keywordFlow = MutableStateFlow(_state.value.search.keyword)

    private var summarySaveJob: Job? = null

    init {
        observeAllEntries()
        observeMonthEntries()
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
                    val calendar = current.calendar
                    val selectedEntry = entries.findEntry(calendar.selectedDate)
                    val previousYears =
                        calendarUseCase.previousYearsOnThisDay(entries, calendar.selectedDate)
                    current.copy(
                        calendar = calendar.copy(
                            selectedEntry = selectedEntry,
                            previousYearsOnThisDay = previousYears,
                        ),
                        search = current.search.copy(
                            availableYears = years,
                            results = runSearch(entries, current.search),
                        ),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMonthEntries() {
        workScope.launch {
            displayMonthFlow
                .flatMapLatest { month -> diaryRepository.observeEntriesInMonth(month) }
                .collectLatest { monthEntries ->
                    val month = displayMonthFlow.value
                    val grid = calendarUseCase.monthGrid(month, monthEntries)
                    _state.update { current ->
                        current.copy(
                            calendar = current.calendar.copy(
                                displayMonth = month,
                                monthGrid = grid,
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

    fun onPreviousMonth() {
        shiftMonth(-1)
    }

    fun onNextMonth() {
        shiftMonth(1)
    }

    fun onJumpToYearMonth(target: YearMonth) {
        if (displayMonthFlow.value == target) return
        displayMonthFlow.value = target
        _state.update { current ->
            current.copy(
                calendar = current.calendar.copy(displayMonth = target),
            )
        }
    }

    fun onSelectDate(date: LocalDate) {
        val entry = allEntries.findEntry(date)
        val previousYears = calendarUseCase.previousYearsOnThisDay(allEntries, date)
        _state.update { current ->
            current.copy(
                calendar = current.calendar.copy(
                    selectedDate = date,
                    selectedEntry = entry,
                    previousYearsOnThisDay = previousYears,
                ),
            )
        }
        val month = YearMonth.from(date)
        if (month != displayMonthFlow.value) {
            displayMonthFlow.value = month
            _state.update { current ->
                current.copy(
                    calendar = current.calendar.copy(displayMonth = month),
                )
            }
        }
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
            current.copy(search = updated.copy(results = runSearch(allEntries, updated)))
        }
    }

    fun onToggleRecordedOnly(value: Boolean) {
        _state.update { current ->
            val updated = current.search.copy(recordedOnly = value)
            current.copy(search = updated.copy(results = runSearch(allEntries, updated)))
        }
    }

    private fun shiftMonth(delta: Long) {
        val target = displayMonthFlow.value.plusMonths(delta)
        onJumpToYearMonth(target)
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

    private fun List<DiaryEntry>.findEntry(date: LocalDate): DiaryEntry? =
        asSequence().filter { it.date == date }.maxByOrNull { it.updatedAt }

    private inline fun MutableStateFlow<ReviewUiState>.update(transform: (ReviewUiState) -> ReviewUiState) {
        value = transform(value)
    }

    companion object {
        const val KEYWORD_DEBOUNCE_MS: Long = 300L
        const val SUMMARY_SAVE_DEBOUNCE_MS: Long = 600L

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReviewViewModel(
                        diaryRepository = application.diaryRepository,
                        annualSummaryRepository = application.annualSummaryRepository,
                        calendarUseCase = application.calendarUseCase,
                        searchUseCase = application.searchUseCase,
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

enum class ReviewTab { Calendar, AnnualSummary, Search }

enum class SaveStatus { Idle, Saving, Saved, Failed }

data class CalendarUiState(
    val displayMonth: YearMonth,
    val monthGrid: List<CalendarDay> = emptyList(),
    val selectedDate: LocalDate,
    val selectedEntry: DiaryEntry? = null,
    val previousYearsOnThisDay: List<DiaryEntry> = emptyList(),
)

data class AnnualUiState(
    val summaries: List<AnnualSummary> = emptyList(),
    val selectedYear: Int? = null,
    val editingContent: String = "",
    val editingSaveStatus: SaveStatus = SaveStatus.Idle,
)

data class SearchUiState(
    val keyword: String = "",
    val yearFilter: Int? = null,
    val recordedOnly: Boolean = true,
    val results: List<DiaryEntry> = emptyList(),
    val availableYears: List<Int> = emptyList(),
)

data class ReviewUiState(
    val selectedTab: ReviewTab,
    val calendar: CalendarUiState,
    val annual: AnnualUiState,
    val search: SearchUiState,
) {
    companion object {
        fun initial(today: LocalDate): ReviewUiState = ReviewUiState(
            selectedTab = ReviewTab.Calendar,
            calendar = CalendarUiState(
                displayMonth = YearMonth.from(today),
                selectedDate = today,
            ),
            annual = AnnualUiState(),
            search = SearchUiState(),
        )
    }
}
