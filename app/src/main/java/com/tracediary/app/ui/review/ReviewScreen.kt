package com.tracediary.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.usecase.CalendarDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MonthTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val PreviousYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private const val ContentPreviewLimit: Int = 60

@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModel.factory(
            LocalContext.current.applicationContext as TraceDiaryApplication,
        ),
    ),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "回顾",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                val tabs = listOf(
                    ReviewTab.Calendar to "日历",
                    ReviewTab.AnnualSummary to "年度总结",
                    ReviewTab.Search to "搜索",
                )
                tabs.forEachIndexed { index, (tab, label) ->
                    SegmentedButton(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        icon = {},
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.surface,
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (state.selectedTab) {
                ReviewTab.Calendar -> CalendarReviewContent(
                    state = state.calendar,
                    onPreviousMonth = viewModel::onPreviousMonth,
                    onNextMonth = viewModel::onNextMonth,
                    onSelectDate = viewModel::onSelectDate,
                )

                ReviewTab.AnnualSummary -> AnnualSummaryContent(
                    state = state.annual,
                    onOpenSummary = viewModel::onOpenSummary,
                    onCloseSummary = viewModel::onCloseSummary,
                    onContentChanged = viewModel::onSummaryContentChanged,
                )

                ReviewTab.Search -> SearchContent(
                    state = state.search,
                    onKeywordChanged = viewModel::onKeywordChanged,
                    onYearFilterChanged = viewModel::onYearFilterChanged,
                )
            }
        }
    }
}

@Composable
private fun CalendarReviewContent(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一月", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = formatMonthTitle(state.displayMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "下一月", modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(4.dp))

                    state.monthGrid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            week.forEach { day ->
                                CalendarDayCell(
                                    day = day,
                                    isSelected = day.date == state.selectedDate,
                                    onClick = { onSelectDate(day.date) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (week.size < 7) {
                                repeat(7 - week.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "往年今日",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        if (state.previousYearsOnThisDay.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        text = "暂无往年今日记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        state.previousYearsOnThisDay.forEachIndexed { index, entry ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = entry.date.format(PreviousYearFormatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = entry.content.preview(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                // TODO: 接入“跳转到该往年记录详情”的导航
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { },
                            )
                            if (index < state.previousYearsOnThisDay.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !day.isInCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(CircleShape)
            .background(baseColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (day.hasRecord) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SelectedDayPreviewCard(state: CalendarUiState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.selectedDate.format(PreviousYearFormatter),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val entry = state.selectedEntry
            if (entry == null || entry.content.isBlank()) {
                Text(
                    text = "这一天还没有记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    text = entry.content.preview(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AnnualSummaryContent(
    state: AnnualUiState,
    onOpenSummary: (Int) -> Unit,
    onCloseSummary: () -> Unit,
    onContentChanged: (String) -> Unit,
) {
    val selectedYear = state.selectedYear
    if (selectedYear == null) {
        AnnualSummaryList(state = state, onOpenSummary = onOpenSummary)
    } else {
        AnnualSummaryEditor(
            year = selectedYear,
            content = state.editingContent,
            saveStatus = state.editingSaveStatus,
            onContentChanged = onContentChanged,
            onClose = onCloseSummary,
        )
    }
}

@Composable
private fun AnnualSummaryList(
    state: AnnualUiState,
    onOpenSummary: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.writableYears.isNotEmpty()) {
            item {
                Text(
                    text = "撰写新年度总结",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        state.writableYears.forEachIndexed { index, year ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "${year}年度总结",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "点击撰写",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onOpenSummary(year) },
                            )
                            if (index < state.writableYears.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "历年年度总结",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.summaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        text = "还没有写过年度总结，从上方任意一年开始吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(state.summaries, key = AnnualSummary::year) { summary ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpenSummary(summary.year) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${summary.year}年度总结",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (summary.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = summary.content.preview(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnualSummaryEditor(
    year: Int,
    content: String,
    saveStatus: SaveStatus,
    onContentChanged: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "${year}年度总结",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = content,
            onValueChange = onContentChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
            placeholder = {
                Text(
                    text = "记下这一年的关键瞬间……",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = saveStatus.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onKeywordChanged: (String) -> Unit,
    onYearFilterChanged: (Int?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.keyword,
            onValueChange = onKeywordChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "搜索日记内容...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.yearFilter == null,
                        onClick = { onYearFilterChanged(null) },
                        label = { Text("全部年份") },
                    )
                    state.availableYears.forEach { year ->
                        FilterChip(
                            selected = state.yearFilter == year,
                            onClick = { onYearFilterChanged(year) },
                            label = { Text("${year}年") },
                        )
                    }
                }
            }
            if (state.results.isEmpty()) {
                item {
                    Text(
                        text = if (state.keyword.isBlank()) "输入关键词查找历史记录" else "没有匹配的记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else {
                items(state.results, key = { it.date.toEpochDay() }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { /* TODO: 跳转到对应日期详情 */ },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = entry.date.format(PreviousYearFormatter),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (entry.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.content.preview(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMonthTitle(month: YearMonth): String =
    month.atDay(1).format(MonthTitleFormatter)

private fun String.preview(): String {
    val trimmed = trim().replace('\n', ' ')
    return if (trimmed.length <= ContentPreviewLimit) trimmed
    else trimmed.take(ContentPreviewLimit) + "…"
}

private fun SaveStatus.label(): String = when (this) {
    SaveStatus.Idle -> ""
    SaveStatus.Saving -> "正在保存…"
    SaveStatus.Saved -> "已保存"
    SaveStatus.Failed -> "保存失败，稍后重试"
}

private fun DiaryEntry.preview(): String = content.preview()
