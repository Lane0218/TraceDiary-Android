package com.tracediary.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.AnnualSummary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EntryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private const val ContentPreviewLimit: Int = 60

@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    onJumpToToday: (LocalDate) -> Unit = {},
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
                    ReviewTab.Search to "搜索",
                    ReviewTab.AnnualSummary to "年度总结",
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
                ReviewTab.Search -> SearchContent(
                    state = state.search,
                    onKeywordChanged = viewModel::onKeywordChanged,
                    onYearFilterChanged = viewModel::onYearFilterChanged,
                    onRecordedOnlyChanged = viewModel::onToggleRecordedOnly,
                    onJumpToToday = onJumpToToday,
                )

                ReviewTab.AnnualSummary -> AnnualSummaryContent(
                    state = state.annual,
                    onOpenSummary = viewModel::onOpenSummary,
                    onCloseSummary = viewModel::onCloseSummary,
                    onContentChanged = viewModel::onSummaryContentChanged,
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
        Box(modifier = Modifier.fillMaxSize()) {
            AnnualSummaryList(state = state, onOpenSummary = onOpenSummary)
            
            if (state.writableYears.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onOpenSummary(state.writableYears.first()) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增年度总结")
                }
            }
        }
    } else {
        AnnualSummaryEditor(
            year = selectedYear,
            content = state.editingContent,
            saveStatus = state.editingSaveStatus,
            onContentChanged = onContentChanged,
            onClose = onCloseSummary,
            onSwitchYear = onOpenSummary
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.summaries.isEmpty() && state.writableYears.isEmpty()) {
            item {
                Text(
                    text = "暂无年度总结",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (state.summaries.isNotEmpty()) {
            item {
                Text(
                    text = "我的年度总结",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
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
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "${summary.year}年度总结",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingContent = {
                            Text(
                                text = summary.updatedAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
    onSwitchYear: (Int) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val wordCount = content.trim().length
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSwitchYear(year - 1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "${year}年",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = { onSwitchYear(year + 1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Text(
                text = "完成",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = content,
            onValueChange = onContentChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                    text = "记下这一年的收获、成长与思考……",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
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
            Text(
                text = saveStatus.label(),
                style = MaterialTheme.typography.labelSmall,
                color = if (saveStatus == SaveStatus.Failed) MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onKeywordChanged: (String) -> Unit,
    onYearFilterChanged: (Int?) -> Unit,
    onRecordedOnlyChanged: (Boolean) -> Unit,
    onJumpToToday: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = CircleShape
        ) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = onKeywordChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "搜索日记内容、关键词...",
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
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.yearFilter == null,
                onClick = { onYearFilterChanged(null) },
                label = { Text("全部年份") },
                shape = CircleShape
            )
            FilterChip(
                selected = state.recordedOnly,
                onClick = { onRecordedOnlyChanged(!state.recordedOnly) },
                label = { Text("仅有记录日期") },
                shape = CircleShape
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.results.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.keyword.isBlank()) "开始回顾你的记录" else "未找到匹配结果",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = if (state.keyword.isBlank()) "搜索关键词或选择条件，查找你的历史日记" else "尝试换一个关键词搜索吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                items(state.results, key = { it.date.toEpochDay() }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onJumpToToday(entry.date) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = entry.date.format(EntryDateFormatter),
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
