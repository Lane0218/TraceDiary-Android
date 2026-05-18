package com.tracediary.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.security.CloudCipherCredentialStore
import com.tracediary.app.data.settings.UserPreferencesRepository
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.model.plainTextWordCount
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.usecase.DiaryCalendarUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 今天页保存状态机：闲置 / 保存中 / 已保存 / 失败。 */
enum class SaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

/** 今天页 UI 状态。 */
data class TodayUiState(
    val selectedDate: LocalDate,
    val weekDates: List<LocalDate>,
    val content: String,
    val wordCount: Int,
    val saveStatus: SaveStatus,
    val previousYearsOnThisDay: List<DiaryEntry>,
    val displayMonthLabel: String,
    val currentEntrySyncState: SyncState?,
    val entryTitle: String,
)

/**
 * 今天页 ViewModel。
 *
 * - 通过 [DiaryRepository] 读写当前日期对应的日记正文，避免 observe 流回写覆盖用户输入。
 * - 文本编辑触发 debounce 自动保存（默认 600ms）。
 * - 切换日期前自动 flush 当前未保存内容，防止丢失。
 */
class TodayViewModel(
    private val diaryRepository: DiaryRepository,
    private val calendarUseCase: DiaryCalendarUseCase,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val secureCredentialStore: CloudCipherCredentialStore? = null,
    private val syncEngine: SyncEngine? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val autoSaveDebounceMillis: Long = DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS,
    private val autoSyncDebounceMillis: Long = DEFAULT_AUTO_SYNC_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val initialDate: LocalDate = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        TodayUiState(
            selectedDate = initialDate,
            weekDates = calendarUseCase.weekDates(initialDate),
            content = "",
            wordCount = 0,
            saveStatus = SaveStatus.Idle,
            previousYearsOnThisDay = emptyList(),
            displayMonthLabel = formatMonthLabel(initialDate),
            currentEntrySyncState = null,
            entryTitle = formatEntryTitle(initialDate),
        )
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    /** 当前正文最后一次入库时的快照，用来判断是否需要 flush。 */
    private var lastPersistedContent: String = ""
    private var autoSaveJob: Job? = null
    private var autoSyncJob: Job? = null
    private var syncStateJob: Job? = null

    init {
        loadEntry(initialDate)
        observeSyncState(initialDate)
    }

    fun onSelectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        viewModelScope.launch {
            flushPendingSave()
            _uiState.update { current ->
                current.copy(
                    selectedDate = date,
                    weekDates = calendarUseCase.weekDates(date),
                    displayMonthLabel = formatMonthLabel(date),
                    entryTitle = formatEntryTitle(date),
                    saveStatus = SaveStatus.Idle,
                )
            }
            loadEntryInternal(date)
            observeSyncState(date)
        }
    }

    fun onPreviousWeek() {
        shiftWeek(-7)
    }

    fun onNextWeek() {
        shiftWeek(7)
    }

    fun onContentChanged(newContent: String) {
        if (newContent == _uiState.value.content) return
        _uiState.update { current ->
            current.copy(
                content = newContent,
                wordCount = newContent.plainTextWordCount(),
                saveStatus = SaveStatus.Saving,
            )
        }
        scheduleAutoSave()
    }

    /** 立即保存当前内容（用于离开页面、切换日期等场景）。 */
    suspend fun flushPendingSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        val state = _uiState.value
        if (state.content == lastPersistedContent) {
            if (state.saveStatus == SaveStatus.Saving) {
                _uiState.update { it.copy(saveStatus = SaveStatus.Saved) }
            }
            return
        }
        persist(state.selectedDate, state.content)
    }

    private fun shiftWeek(deltaDays: Long) {
        _uiState.update { current ->
            val pivot = current.weekDates.firstOrNull() ?: current.selectedDate
            val newPivot = pivot.plusDays(deltaDays)
            current.copy(
                weekDates = calendarUseCase.weekDates(newPivot),
                displayMonthLabel = formatMonthLabel(newPivot),
            )
        }
    }

    private fun loadEntry(date: LocalDate) {
        viewModelScope.launch {
            loadEntryInternal(date)
        }
    }

    private suspend fun loadEntryInternal(date: LocalDate) {
        val entry = withContext(ioDispatcher) { diaryRepository.getEntry(date) }
        val previous = withContext(ioDispatcher) { diaryRepository.getSameMonthDayEntries(date) }
        val content = entry?.content.orEmpty()
        lastPersistedContent = content
        _uiState.update { current ->
            current.copy(
                content = content,
                wordCount = content.plainTextWordCount(),
                previousYearsOnThisDay = previous,
                currentEntrySyncState = entry?.syncState,
                // 已经落库过的条目展示“已保存”，让用户离开后回来仍能确认状态；空白条目保持“未编辑”。
                saveStatus = if (entry != null && entry.content.isNotEmpty()) SaveStatus.Saved else SaveStatus.Idle,
            )
        }
    }

    private fun observeSyncState(date: LocalDate) {
        syncStateJob?.cancel()
        syncStateJob = viewModelScope.launch {
            diaryRepository.observeEntry(date).collect { entry ->
                _uiState.update { current ->
                    if (current.selectedDate == date) {
                        current.copy(currentEntrySyncState = entry?.syncState)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(autoSaveDebounceMillis)
            val snapshot = _uiState.value
            if (snapshot.content == lastPersistedContent) {
                _uiState.update { it.copy(saveStatus = SaveStatus.Saved) }
                return@launch
            }
            persist(snapshot.selectedDate, snapshot.content)
        }
    }

    private suspend fun persist(date: LocalDate, content: String) {
        try {
            val saved = withContext(ioDispatcher) {
                diaryRepository.saveEntry(date, content)
            }
            lastPersistedContent = saved.content
            scheduleAutoSync(date)
            // 仅当用户没有继续编辑（即仍处于刚刚保存的内容）时才把状态置为 Saved。
            _uiState.update { current ->
                if (current.selectedDate == date && current.content == saved.content) {
                    current.copy(
                        saveStatus = SaveStatus.Saved,
                        currentEntrySyncState = saved.syncState,
                    )
                } else {
                    current.copy(currentEntrySyncState = saved.syncState)
                }
            }
        } catch (t: Throwable) {
            _uiState.update { current ->
                if (current.selectedDate == date) {
                    current.copy(saveStatus = SaveStatus.Failed)
                } else {
                    current
                }
            }
        }
    }

    private suspend fun scheduleAutoSync(date: LocalDate) {
        val preferencesRepository = userPreferencesRepository ?: return
        val store = secureCredentialStore ?: return
        val engine = syncEngine ?: return
        val prefs = preferencesRepository.preferences.first()
        if (!prefs.syncEnabled) return
        if (store.loadLocalPassword().isNullOrEmpty()) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(autoSyncDebounceMillis)
            withContext(ioDispatcher) {
                engine.uploadDiary(date)
            }
        }
    }

    private fun formatMonthLabel(date: LocalDate): String =
        "${date.year}年${date.monthValue}月"

    private fun formatEntryTitle(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
        return "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }

    companion object {
        const val DEFAULT_AUTO_SAVE_DEBOUNCE_MILLIS: Long = 600L
        const val DEFAULT_AUTO_SYNC_DEBOUNCE_MILLIS: Long = 5_000L

        fun factory(application: TraceDiaryApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TodayViewModel(
                        diaryRepository = application.diaryRepository,
                        calendarUseCase = application.calendarUseCase,
                        userPreferencesRepository = application.userPreferencesRepository,
                        secureCredentialStore = application.secureCredentialStore,
                        syncEngine = application.syncEngine,
                    )
                }
            }
    }
}
