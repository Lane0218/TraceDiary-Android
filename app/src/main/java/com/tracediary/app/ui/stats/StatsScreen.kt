package com.tracediary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.usecase.DiaryStatistics
import com.tracediary.app.domain.usecase.HeatmapDay
import com.tracediary.app.domain.usecase.MonthlyWordCount
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

import com.tracediary.app.ui.components.TraceDropdown

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(
            LocalContext.current.applicationContext as TraceDiaryApplication,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(
        modifier = modifier,
        state = state,
        onSelectYear = viewModel::onSelectYear,
        onSelectTrendYear = viewModel::onSelectTrendYear,
    )
}

@Composable
internal fun StatsContent(
    modifier: Modifier = Modifier,
    state: StatsUiState,
    onSelectYear: (Int) -> Unit,
    onSelectTrendYear: (Int) -> Unit,
) {
    val statistics = state.statistics
    if (state.isLoading || statistics == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OverviewGrid(statistics)
        }

        item {
            MonthlyTrendCard(
                title = "字数趋势",
                year = state.selectedTrendYear,
                availableYears = state.availableYears,
                data = statistics.monthlyWordTrend,
                onSelectYear = onSelectTrendYear,
            )
        }

        item {
            HeatmapCard(
                year = state.selectedYear,
                availableYears = state.availableYears,
                heatmap = statistics.yearlyHeatmap,
                onSelectYear = onSelectYear,
            )
        }
    }
}

@Composable
private fun OverviewGrid(statistics: DiaryStatistics) {
    val totalWord = formatWordCount(statistics.totalWordCount)
    val yearWord = formatWordCount(statistics.yearWordCount)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val row1 = listOf(
            StatsData("总日记", statistics.totalEntries.toString(), "篇", Icons.Outlined.Description),
            StatsData("累计字数", totalWord.first, totalWord.second, Icons.Outlined.TextFormat),
            StatsData("本年字数", yearWord.first, yearWord.second, Icons.Outlined.EditCalendar),
        )
        val row2 = listOf(
            StatsData("本月记录", statistics.monthRecordCount.toString(), "天", Icons.Outlined.CalendarMonth),
            StatsData("连续记录", statistics.currentStreakDays.toString(), "天", Icons.Outlined.LocalFireDepartment),
            StatsData("全年记录", statistics.annualRecordCount.toString(), "天", Icons.Outlined.BarChart),
        )
        DataGridRow(row1)
        DataGridRow(row2)
    }
}

@Composable
private fun DataGridRow(dataList: List<StatsData>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        dataList.forEach { item -> BentoDataCard(item, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun BentoDataCard(data: StatsData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = data.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = data.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = data.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Text(text = data.unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun MonthlyTrendCard(
    title: String,
    year: Int,
    availableYears: List<Int>,
    data: List<MonthlyWordCount>,
    onSelectYear: (Int) -> Unit,
) {
    var selectedMonth by remember(year) { mutableStateOf<YearMonth?>(null) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TraceDropdown(
                    label = "${year}年",
                    options = availableYears,
                    optionLabel = { "${it}年" },
                    onSelect = onSelectYear,
                )
            }
            val maxVal = data.maxOfOrNull(MonthlyWordCount::wordCount)?.coerceAtLeast(1) ?: 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                data.forEachIndexed { index, entry ->
                    val ratio = entry.wordCount.toFloat() / maxVal
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(128.dp)
                                .semantics { contentDescription = "${entry.yearMonth.year}年${entry.yearMonth.monthValue}月，${entry.wordCount}字" }
                                .clickable(
                                    interactionSource = remember(entry.yearMonth) { MutableInteractionSource() },
                                    indication = null,
                                ) { selectedMonth = entry.yearMonth },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (selectedMonth == entry.yearMonth) {
                                    Text(
                                        text = formatMonthlyTrendWordCount(entry.wordCount),
                                        modifier = Modifier.wrapContentWidth(unbounded = true),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((100 * ratio).dp.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (selectedMonth == null || selectedMonth == entry.yearMonth) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            },
                                        ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (index % 2 == 0) {
                            Text(
                                text = "${entry.yearMonth.monthValue}月",
                                modifier = Modifier
                                    .wrapContentWidth(unbounded = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(
    year: Int,
    availableYears: List<Int>,
    heatmap: List<HeatmapDay>,
    onSelectYear: (Int) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "年度热力图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TraceDropdown(
                    label = "${year}年",
                    options = availableYears,
                    optionLabel = { "${it}年" },
                    onSelect = onSelectYear,
                )
            }

            val daysByMonth = remember(heatmap) {
                heatmap.groupBy { YearMonth.from(it.date) }
            }

            // 4 行 x 3 列的月份微缩方阵
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (col in 0 until 3) {
                            val month = row * 3 + col + 1
                            val yearMonth = YearMonth.of(year, month)
                            MiniMonthGrid(
                                yearMonth = yearMonth,
                                days = daysByMonth[yearMonth].orEmpty(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "未记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "已记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun MiniMonthGrid(
    yearMonth: YearMonth,
    days: List<HeatmapDay>,
    modifier: Modifier = Modifier,
) {
    val daysByDate = remember(days) { days.associateBy { it.date } }
    val firstDay = yearMonth.atDay(1)
    val weekFields = remember { WeekFields.of(Locale.getDefault()) }
    val firstDayOfWeek = weekFields.firstDayOfWeek
    // 计算 1 号在网格中相对左上角的偏移
    val offsetFromFirst = ((firstDay.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7

    Column(modifier = modifier) {
        Text(
            text = "${yearMonth.monthValue}月",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (w in 0 until 6) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (d in 0 until 7) {
                        val cellIndex = w * 7 + d
                        val dayOfMonth = cellIndex - offsetFromFirst + 1
                        val color = if (dayOfMonth in 1..yearMonth.lengthOfMonth()) {
                            val date = LocalDate.of(yearMonth.year, yearMonth.monthValue, dayOfMonth)
                            daysByDate[date].toCellColor()
                        } else {
                            Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapDay?.toCellColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    return if (this?.hasRecord == true) primary else empty
}

private data class StatsData(val label: String, val value: String, val unit: String, val icon: ImageVector)

internal fun formatMonthlyTrendWordCount(wordCount: Int): String =
    if (wordCount < 1000) wordCount.toString()
    else String.format(Locale.CHINA, "%.1fk", wordCount / 1000.0)

/**
 * 字数格式化：>=1000 时按 "12.8" / "k" 表示（保留 1 位小数，整数则去掉小数位），
 * 否则原数字 + "字"。
 *
 * 返回 (数值文本, 单位文本)。
 */
internal fun formatWordCount(value: Int): Pair<String, String> {
    if (value < 1000) return value.toString() to "字"
    // 截断到 1 位小数，避免四舍五入造成数据被夸大
    val tenths = value / 100
    val intPart = tenths / 10
    val decimalPart = tenths % 10
    val text = if (decimalPart == 0) intPart.toString() else "$intPart.$decimalPart"
    return text to "k"
}
