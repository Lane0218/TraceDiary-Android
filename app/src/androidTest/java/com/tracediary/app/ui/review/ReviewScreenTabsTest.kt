package com.tracediary.app.ui.review

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.domain.usecase.SearchDiaryEntriesUseCase
import com.tracediary.app.ui.theme.TraceDiaryTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * 验证回顾页 SegmentedButton 两个 Tab（搜索 / 年度总结）切换后内容区会随之变化，
 * 对应 SPEC 6.1.4。
 *
 * 数据源使用空仓储：
 * - 此时 ReviewViewModel.observeAnnualSummaries 计算出的 writableYears = 当年起向前 5 年，
 *   因此年度总结 Tab 必然展示空状态与新增按钮。
 */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTabsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-05-17T00:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun annualSummaryTabShowsWritePrompt() {
        val viewModel = ReviewViewModel(
            diaryRepository = EmptyDiaryRepository(),
            annualSummaryRepository = EmptyAnnualSummaryRepository(),
            calendarUseCase = DiaryCalendarUseCase(),
            searchUseCase = SearchDiaryEntriesUseCase(),
            clock = fixedClock,
        )

        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewScreen(viewModel = viewModel)
                }
            }
        }

        composeTestRule.waitForIdle()

        // 切到「年度总结」
        composeTestRule.onNodeWithText("年度总结").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("尚未撰写年度总结").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("新增年度总结").assertIsDisplayed()
    }

    @Test
    fun defaultTabIsSearch() {
        val viewModel = ReviewViewModel(
            diaryRepository = EmptyDiaryRepository(),
            annualSummaryRepository = EmptyAnnualSummaryRepository(),
            calendarUseCase = DiaryCalendarUseCase(),
            searchUseCase = SearchDiaryEntriesUseCase(),
            clock = fixedClock,
        )

        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewScreen(viewModel = viewModel)
                }
            }
        }

        composeTestRule.waitForIdle()

        // 默认就是搜索 Tab，placeholder 已可见。
        composeTestRule.onNodeWithText("搜索日记内容、关键词...").assertIsDisplayed()
    }

    /** 空 DiaryRepository，仅满足 ReviewViewModel 构造与初始 collect。 */
    private class EmptyDiaryRepository : DiaryRepository {
        private val entries = MutableStateFlow<List<DiaryEntry>>(emptyList())

        override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> =
            entries.map { list -> list.firstOrNull { it.date == date } }

        override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
            entries.map { list -> list.filter { YearMonth.from(it.date) == month } }

        override fun observeRecordedDates(): Flow<List<LocalDate>> =
            entries.map { list -> list.map(DiaryEntry::date) }

        override fun observeAllEntries(): Flow<List<DiaryEntry>> = entries.asStateFlow()

        override suspend fun getEntry(date: LocalDate): DiaryEntry? = null
        override suspend fun getEntriesBetween(
            start: LocalDate,
            endInclusive: LocalDate,
        ): List<DiaryEntry> = emptyList()

        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry =
            throw UnsupportedOperationException("not used")

        override suspend fun deleteEntry(date: LocalDate) = Unit

    }

    private class EmptyAnnualSummaryRepository : AnnualSummaryRepository {
        private val summaries = MutableStateFlow<List<AnnualSummary>>(emptyList())

        override fun observeSummary(year: Int): Flow<AnnualSummary?> =
            summaries.map { list -> list.firstOrNull { it.year == year } }

        override fun observeSummaries(): Flow<List<AnnualSummary>> = summaries.asStateFlow()

        override suspend fun getSummary(year: Int): AnnualSummary? = null

        override suspend fun saveSummary(year: Int, content: String): AnnualSummary =
            throw UnsupportedOperationException("not used")

        override suspend fun deleteSummary(year: Int) = Unit
    }
}
