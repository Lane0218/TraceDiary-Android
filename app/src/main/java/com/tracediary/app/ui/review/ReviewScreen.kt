package com.tracediary.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
import com.tracediary.app.ui.theme.DiaryContentTextStyle
import com.tracediary.app.ui.theme.DiaryExcerptTextStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    ReviewTab.Search to "搜索",
                    ReviewTab.AnnualSummary to "年度总结",
                )
                tabs.forEachIndexed { index, pair ->
                    val (tab, label) = pair
                    val selected = state.selectedTab == tab
                    val bgShape = if (index == 0) {
                        androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                    } else {
                        androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(bgShape)
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { viewModel.onSelectTab(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .size(width = 24.dp, height = 2.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
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
                    onDeleteSummary = viewModel::onDeleteSummary,
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
    onDeleteSummary: () -> Unit,
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
            onSwitchYear = onOpenSummary,
            onDelete = onDeleteSummary,
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
        if (state.summaries.isEmpty()) {
            item {
                ReviewEmptyState(
                    title = "尚未撰写年度总结",
                    description = "点击右下角的“+”按钮，开启你的年度回顾与总结吧",
                    modifier = Modifier.fillParentMaxHeight(),
                )
            }
        }

        if (state.summaries.isNotEmpty()) {
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
                                text = "${summary.year}年",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingContent = {
                            Text(
                                text = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                                    .withZone(ZoneId.systemDefault())
                                    .format(summary.updatedAt),
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
    onDelete: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val wordCount = content.trim().length
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSwitchYear(year - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上一年",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "${year}年",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { onSwitchYear(year + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下一年",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("删除该年度总结") },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除${year}年的年度总结？") },
                text = { Text("删除将移除该年度总结的本地记录，若已启用云同步会同时尝试删除云端对象，操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                }
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
                    style = DiaryContentTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            },
            textStyle = DiaryContentTextStyle,
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
    var yearDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextField(
            value = state.keyword,
            onValueChange = onKeywordChanged,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    onClick = { yearDropdownExpanded = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.yearFilter?.let { "${it}年" } ?: "全部年份",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                androidx.compose.material3.DropdownMenu(
                    expanded = yearDropdownExpanded,
                    onDismissRequest = { yearDropdownExpanded = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("全部年份") },
                        onClick = {
                            onYearFilterChanged(null)
                            yearDropdownExpanded = false
                        }
                    )
                    state.availableYears.forEach { year ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${year}年") },
                            onClick = {
                                onYearFilterChanged(year)
                                yearDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.keyword.isBlank()) {
                item {
                    ReviewEmptyState(
                        title = "开始回顾你的记录",
                        description = "搜索关键词或选择条件，查找你的历史日记",
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }
            } else if (state.results.isEmpty()) {
                item {
                    ReviewEmptyState(
                        title = "未找到匹配结果",
                        description = "尝试换一个关键词搜索吧",
                        modifier = Modifier.fillParentMaxHeight(),
                    )
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
                                
                                val previewText = entry.content.preview()
                                val annotatedText = buildAnnotatedString {
                                    var currentIndex = 0
                                    var matchIndex = previewText.indexOf(state.keyword, startIndex = currentIndex, ignoreCase = true)
                                    
                                    while (matchIndex >= 0) {
                                        append(previewText.substring(currentIndex, matchIndex))
                                        withStyle(style = SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )) {
                                            append(previewText.substring(matchIndex, matchIndex + state.keyword.length))
                                        }
                                        currentIndex = matchIndex + state.keyword.length
                                        matchIndex = previewText.indexOf(state.keyword, startIndex = currentIndex, ignoreCase = true)
                                    }
                                    append(previewText.substring(currentIndex))
                                }
                                
                                Text(
                                    text = annotatedText,
                                    style = DiaryExcerptTextStyle,
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

@Composable
private fun ReviewEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
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
