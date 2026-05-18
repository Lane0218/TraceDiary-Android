package com.tracediary.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    pendingDate: LocalDate? = null,
    onPendingDateConsumed: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.factory(
            LocalContext.current.applicationContext as TraceDiaryApplication
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 消费来自回顾页的跳转日期
    LaunchedEffect(pendingDate) {
        if (pendingDate != null) {
            viewModel.onSelectDate(pendingDate)
            onPendingDateConsumed()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "今天", style = MaterialTheme.typography.displayLarge)
                TodayOverflowMenu(
                    selectedDate = state.selectedDate,
                    onPickDate = viewModel::onSelectDate,
                    onClearContent = { viewModel.onContentChanged("") },
                )
            }
        }

        item {
            WeekCalendarCard(
                weekDates = state.weekDates,
                selectedDate = state.selectedDate,
                monthLabel = state.displayMonthLabel,
                onPrevious = viewModel::onPreviousWeek,
                onNext = viewModel::onNextWeek,
                onSelectDate = viewModel::onSelectDate,
            )
        }

        item {
            EntryEditorCard(
                title = state.entryTitle,
                content = state.content,
                wordCount = state.wordCount,
                saveStatus = state.saveStatus,
                syncState = state.currentEntrySyncState,
                onContentChanged = viewModel::onContentChanged,
            )
        }

        item {
            Text(
                text = "往年今日",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        if (state.previousYearsOnThisDay.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "暂无往年记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = state.previousYearsOnThisDay,
                key = { entry -> entry.date.toEpochDay() }
            ) { entry ->
                PreviousYearEntryCard(
                    entry = entry,
                    onClick = { viewModel.onSelectDate(entry.date) },
                )
            }
        }
    }
}

@Composable
private fun WeekCalendarCard(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    monthLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上一周",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下一周",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDates.forEach { date ->
                    val isSelected = date == selectedDate
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // 修复 Hover：先 clip 再 clickable
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryEditorCard(
    title: String,
    content: String,
    wordCount: Int,
    saveStatus: SaveStatus,
    syncState: SyncState?,
    onContentChanged: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = content,
                onValueChange = onContentChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                placeholder = {
                    Text(
                        text = "记录今天发生了什么…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordCount 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                SaveStatusIndicator(saveStatus = saveStatus, syncState = syncState)
            }
        }
    }
}

@Composable
private fun SaveStatusIndicator(saveStatus: SaveStatus, syncState: SyncState?) {
    val (label, icon, tint) = when (saveStatus) {
        SaveStatus.Idle -> Triple(
            "未编辑",
            Icons.Outlined.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        SaveStatus.Saving -> Triple(
            "保存中…",
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        SaveStatus.Saved -> Triple(
            "已保存",
            Icons.Outlined.CheckCircle,
            MaterialTheme.colorScheme.primary
        )
        SaveStatus.Failed -> Triple(
            "保存失败",
            Icons.Outlined.ErrorOutline,
            MaterialTheme.colorScheme.error
        )
    }
    // 仅在条目已落库（saveStatus 非 Idle 且存在同步态）时拼接同步态文案，避免新建未保存条目误显示。
    val syncLabel: String? = if (saveStatus != SaveStatus.Idle && syncState != null) {
        when (syncState) {
            SyncState.Synced -> "云同步完成"
            SyncState.Pending -> "等待云同步"
            SyncState.LocalOnly -> "仅本地保存"
            SyncState.Failed -> "云同步失败"
            SyncState.Conflict -> "云同步冲突"
        }
    } else {
        null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        if (syncLabel != null) {
            Text(
                text = " · $syncLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
    }
}

@Composable
private fun PreviousYearEntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit,
) {
    val title = "${entry.date.year}年${entry.date.monthValue}月${entry.date.dayOfMonth}日"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 修复 Hover
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayOverflowMenu(
    selectedDate: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    onClearContent: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("跳到指定日期") },
                onClick = {
                    menuExpanded = false
                    showDatePicker = true
                }
            )
            DropdownMenuItem(
                text = { Text("清空当日内容") },
                onClick = {
                    menuExpanded = false
                    showClearConfirm = true
                }
            )
        }
    }

    if (showDatePicker) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        // SPEC: 仅允许补写历史，不允许写未来。
        val todayMillis = remember {
            LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int): Boolean =
                    year <= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis
                    if (picked != null) {
                        val date = Instant.ofEpochMilli(picked)
                            .atZone(zone)
                            .toLocalDate()
                        onPickDate(date)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当天内容？") },
            text = { Text("清空后无法恢复，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    onClearContent()
                    showClearConfirm = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}
