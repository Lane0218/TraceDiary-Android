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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.usecase.CalendarDay
import com.tracediary.app.ui.theme.DiaryContentTextStyle
import com.tracediary.app.ui.theme.DiaryEditorTextStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

import com.tracediary.app.ui.components.TraceDropdownMenu

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
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { viewModel.onJumpToToday() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TodayOverflowMenu(
                    displayMonth = state.displayMonth,
                    onJumpToMonth = viewModel::onJumpToMonth,
                    onClearContent = viewModel::onDeleteCurrentEntry,
                )
            }
        }

        item {
            CalendarCard(
                weekDays = state.weekDays,
                selectedDate = state.selectedDate,
                monthLabel = state.displayMonthLabel,
                displayMonth = state.displayMonth,
                monthGrid = state.monthGrid,
                isMonthExpanded = state.isMonthExpanded,
                onPreviousWeek = viewModel::onPreviousWeek,
                onNextWeek = viewModel::onNextWeek,
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onToggleMonthExpand = viewModel::onToggleMonthExpand,
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
                deleteErrorMessage = state.deleteErrorMessage,
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
private fun CalendarCard(
    weekDays: List<CalendarDay>,
    selectedDate: LocalDate,
    monthLabel: String,
    displayMonth: YearMonth,
    monthGrid: List<CalendarDay>,
    isMonthExpanded: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleMonthExpand: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题区：左右切换按钮 + 月份 + 展开/收起
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = if (isMonthExpanded) onPreviousMonth else onPreviousWeek,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = if (isMonthExpanded) "上一月" else "上一周",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onToggleMonthExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isMonthExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (isMonthExpanded) "收起月历" else "展开月历",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(
                    onClick = if (isMonthExpanded) onNextMonth else onNextWeek,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = if (isMonthExpanded) "下一月" else "下一周",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (isMonthExpanded) {
                MonthGrid(
                    monthGrid = monthGrid,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                )
            } else {
                WeekRow(
                    weekDays = weekDays,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                )
            }
        }
    }
}

@Composable
private fun WeekRow(
    weekDays: List<CalendarDay>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WeekHeaderRow()
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                CalendarDayCell(
                    day = day,
                    isSelected = day.date == selectedDate,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDate(day.date) },
                    showWeekdayLabel = false,
                    cellShape = MaterialTheme.shapes.medium,
                    cellPadding = PaddingValues(top = 8.dp, bottom = 6.dp),
                    dayTextStyle = MaterialTheme.typography.titleMedium,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    showInCurrentMonthIndicator = true,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    monthGrid: List<CalendarDay>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WeekHeaderRow()
        Spacer(modifier = Modifier.height(4.dp))
        monthGrid.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        isSelected = day.date == selectedDate,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDate(day.date) },
                        showWeekdayLabel = false,
                        cellShape = CircleShape,
                        cellPadding = PaddingValues(top = 6.dp, bottom = 4.dp),
                        dayTextStyle = MaterialTheme.typography.bodyMedium,
                        unselectedTextColor = if (!day.isInCurrentMonth) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        showInCurrentMonthIndicator = true,
                    )
                }
                if (week.size < 7) {
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        if (monthGrid.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WeekHeaderRow() {
    val weekHeaders = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekHeaders.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWeekdayLabel: Boolean,
    cellShape: androidx.compose.ui.graphics.Shape,
    cellPadding: PaddingValues,
    dayTextStyle: androidx.compose.ui.text.TextStyle,
    unselectedTextColor: Color,
    selectedContainerColor: Color,
    selectedTextColor: Color,
    showInCurrentMonthIndicator: Boolean,
) {
    val containerColor = if (isSelected) selectedContainerColor else Color.Transparent
    val resolvedTextColor = if (isSelected) selectedTextColor else unselectedTextColor
    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(cellShape)
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(cellPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showWeekdayLabel) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = day.date.dayOfMonth.toString(),
                style = dayTextStyle,
                color = resolvedTextColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (showInCurrentMonthIndicator && day.hasRecord) {
                val badgeColor = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                )
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
    deleteErrorMessage: String?,
    onContentChanged: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
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
                        style = DiaryEditorTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                },
                textStyle = DiaryEditorTextStyle,
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
            if (!deleteErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = deleteErrorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
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

@Composable
private fun TodayOverflowMenu(
    displayMonth: YearMonth,
    onJumpToMonth: (YearMonth) -> Unit,
    onClearContent: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
        }
        TraceDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("跳到指定月份") },
                onClick = {
                    menuExpanded = false
                    showMonthPicker = true
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

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = displayMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { picked ->
                showMonthPicker = false
                onJumpToMonth(picked)
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当天内容？") },
            text = { Text("清空将删除该日期的本地记录，若已启用云同步会同时尝试删除云端对象，操作不可恢复。") },
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

@Composable
private fun YearMonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    val today = remember { YearMonth.now() }
    var displayYear by remember { mutableStateOf(initialYearMonth.year) }
    var selectedMonth by remember { mutableStateOf(initialYearMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { displayYear-- },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上一年",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "${displayYear}年",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { displayYear++ },
                    enabled = displayYear < today.year,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下一年",
                        modifier = Modifier.size(20.dp),
                        tint = if (displayYear < today.year) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }
        },
        text = {
            val months = listOf(
                "1月", "2月", "3月", "4月",
                "5月", "6月", "7月", "8月",
                "9月", "10月", "11月", "12月",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { label ->
                            val monthValue = months.indexOf(label) + 1
                            val ym = YearMonth.of(displayYear, monthValue)
                            val isFuture = ym.isAfter(today)
                            val isSelected = ym == selectedMonth
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = !isFuture) {
                                        selectedMonth = ym
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMonth) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
