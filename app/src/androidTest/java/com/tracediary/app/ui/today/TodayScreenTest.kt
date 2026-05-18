package com.tracediary.app.ui.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import com.tracediary.app.ui.theme.TraceDiaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
 * TodayScreen Compose UI 测试，对应 SPEC 6.1.4：
 * - debounce 自动保存后状态文案变为「已保存」
 * - 切换周历前后按钮不会改变 selectedDate
 * - 字数随输入实时更新
 *
 * 注意：使用 [composeTestRule.mainClock.advanceTimeBy] 推进时间，
 * 因此 ViewModel 内部的 [kotlinx.coroutines.delay] 必须运行在与 mainClock 关联的
 * Main dispatcher 上；这里把 ioDispatcher 同样配置为 [Dispatchers.Main] 以保证
 * 协程在 mainClock 控制下推进。
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 5, 17)
    private val fixedClock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneOffset.UTC).toInstant(),
        ZoneOffset.UTC,
    )

    @Test
    fun debouncedAutoSaveTogglesStatusToSaved() {
        val repository = InMemoryDiaryRepository()
        val viewModel = TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            clock = fixedClock,
            // 让 IO 调度也在 mainClock 控制下，确保 mainClock.advanceTimeBy 能驱动落库。
            ioDispatcher = Dispatchers.Main,
            autoSaveDebounceMillis = 600L,
        )

        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TodayScreen(viewModel = viewModel)
                }
            }
        }

        // 等待 init 中的 loadEntry 完成
        composeTestRule.waitForIdle()

        // 输入前应处于「未编辑」状态
        composeTestRule.onNodeWithText("未编辑").assertIsDisplayed()

        // 输入正文，触发 onContentChanged → SaveStatus.Saving
        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performTextInput("今天散步")
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.onNodeWithText("保存中…").assertIsDisplayed()

        // 推进 700ms 跨过 600ms debounce，应转为「已保存」
        composeTestRule.mainClock.advanceTimeBy(700)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已保存").assertIsDisplayed()
    }

    @Test
    fun weekArrowsKeepSelectedDate() {
        val repository = InMemoryDiaryRepository()
        val viewModel = TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            clock = fixedClock,
            ioDispatcher = Dispatchers.Main,
        )

        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TodayScreen(viewModel = viewModel)
                }
            }
        }

        composeTestRule.waitForIdle()

        // 标题为「5月17日 星期日」
        val expectedTitle = "5月17日 星期日"
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescriptionSafe("上一周").performClick()
        composeTestRule.onNodeWithContentDescriptionSafe("下一周").performClick()
        composeTestRule.onNodeWithContentDescriptionSafe("下一周").performClick()
        composeTestRule.waitForIdle()

        // selectedDate 没变，标题保持原样
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun wordCountUpdatesAsTyping() {
        val repository = InMemoryDiaryRepository()
        val viewModel = TodayViewModel(
            diaryRepository = repository,
            calendarUseCase = DiaryCalendarUseCase(),
            clock = fixedClock,
            ioDispatcher = Dispatchers.Main,
        )

        composeTestRule.setContent {
            TraceDiaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TodayScreen(viewModel = viewModel)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("0 字").assertIsDisplayed()

        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performTextInput("一二三")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3 字").assertIsDisplayed()

        // 用 replacement 覆盖输入，避免 IME 拼接歧义
        composeTestRule.onNode(
            androidx.compose.ui.test.hasSetTextAction(),
        ).performTextReplacement("一二三四五")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("5 字").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionSafe(
        description: String,
    ) = onNode(
        androidx.compose.ui.test.hasContentDescription(description),
        useUnmergedTree = true,
    )

    /** 内存版 DiaryRepository，覆盖 TodayViewModel 用到的方法。 */
    private class InMemoryDiaryRepository : DiaryRepository {
        private val store = MutableStateFlow<Map<LocalDate, DiaryEntry>>(emptyMap())

        override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> =
            store.map { it[date] }

        override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
            flowOf(emptyList())

        override fun observeRecordedDates(): Flow<List<LocalDate>> = flowOf(emptyList())

        override fun observeAllEntries(): Flow<List<DiaryEntry>> =
            store.map { it.values.sortedBy(DiaryEntry::date) }

        override suspend fun getEntry(date: LocalDate): DiaryEntry? = store.value[date]

        override suspend fun getEntriesBetween(
            start: LocalDate,
            endInclusive: LocalDate,
        ): List<DiaryEntry> = store.value.values
            .filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }
            .sortedBy(DiaryEntry::date)

        override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
            val existing = store.value[date]
            val now = Instant.parse("2026-05-17T00:00:00Z")
            val entry = DiaryEntry(
                date = date,
                content = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = if (existing?.content == content) existing.updatedAt else now,
                syncState = if (existing?.content == content) existing.syncState else SyncState.Pending,
            )
            store.value = store.value.toMutableMap().apply { put(date, entry) }
            return entry
        }

        override suspend fun deleteEntry(date: LocalDate) {
            store.value = store.value.toMutableMap().apply { remove(date) }
        }

        override suspend fun searchEntries(
            query: String,
            year: Int?,
            recordedOnly: Boolean,
        ): List<DiaryEntry> = emptyList()

        override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> =
            store.value.values
                .filter { it.date.isBefore(date) }
                .filter { it.date.month == date.month && it.date.dayOfMonth == date.dayOfMonth }
                .sortedByDescending(DiaryEntry::date)
    }
}
