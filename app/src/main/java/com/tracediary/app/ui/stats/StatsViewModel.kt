package com.tracediary.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryStatistics
import com.tracediary.app.domain.usecase.DiaryStatisticsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate

/**
 * 统计页 UI 状态。
 *
 * @property selectedYear 概览与热力图所选年份。
 * @property selectedTrendYear 月度趋势所选年份；默认与 [selectedYear] 联动。
 * @property statistics 已计算好的统计结果，加载完成后非 null。
 * @property availableYears 可选年份集合（来自现有 entries），倒序，至少包含今年。
 */
data class StatsUiState(
    val isLoading: Boolean,
    val referenceDate: LocalDate,
    val selectedYear: Int,
    val selectedTrendYear: Int,
    val statistics: DiaryStatistics?,
    val availableYears: List<Int>,
)

class StatsViewModel(
    private val diaryRepository: DiaryRepository,
    private val statisticsUseCase: DiaryStatisticsUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val selectedYearFlow = MutableStateFlow(today.year)
    private val selectedTrendYearFlow = MutableStateFlow(today.year)

    val uiState: StateFlow<StatsUiState> = combine(
        diaryRepository.observeAllEntries(),
        selectedYearFlow,
        selectedTrendYearFlow,
    ) { entries, selectedYear, selectedTrendYear ->
        buildState(entries, selectedYear, selectedTrendYear)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatsUiState(
            isLoading = true,
            referenceDate = today,
            selectedYear = today.year,
            selectedTrendYear = today.year,
            statistics = null,
            availableYears = listOf(today.year),
        ),
    )

    /** 切换概览/热力图年份；同时联动 trend 年份，简化 UI 交互。 */
    fun onSelectYear(year: Int) {
        selectedYearFlow.update { year }
        selectedTrendYearFlow.update { year }
    }

    /** 仅切换月度趋势卡片所属年份。 */
    fun onSelectTrendYear(year: Int) {
        selectedTrendYearFlow.update { year }
    }

    private fun buildState(
        entries: List<DiaryEntry>,
        selectedYear: Int,
        selectedTrendYear: Int,
    ): StatsUiState {
        val referenceDate = resolveReferenceDate(selectedYear)
        // 概览/热力图按 selectedYear 计算
        val baseStats = statisticsUseCase.calculate(entries, referenceDate)
        // 趋势若与 selectedYear 不同，则单独再算一次仅替换 monthlyWordTrend
        val statistics = if (selectedTrendYear == selectedYear) {
            baseStats
        } else {
            baseStats.copy(
                monthlyWordTrend = statisticsUseCase.calculateMonthlyWordTrend(
                    entries,
                    selectedTrendYear,
                ),
            )
        }
        val availableYears = buildAvailableYears(entries)
        return StatsUiState(
            isLoading = false,
            referenceDate = referenceDate,
            selectedYear = selectedYear,
            selectedTrendYear = selectedTrendYear,
            statistics = statistics,
            availableYears = availableYears,
        )
    }

    private fun resolveReferenceDate(selectedYear: Int): LocalDate =
        if (selectedYear == today.year) today else LocalDate.of(selectedYear, 12, 31)

    private fun buildAvailableYears(entries: List<DiaryEntry>): List<Int> {
        val years = entries.map { it.date.year }.toMutableSet()
        years += today.year
        return years.sortedDescending()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    StatsViewModel(
                        diaryRepository = application.diaryRepository,
                        statisticsUseCase = application.statisticsUseCase,
                    )
                }
            }
    }
}
