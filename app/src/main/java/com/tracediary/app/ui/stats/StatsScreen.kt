package com.tracediary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onTrendMonthClick: () -> Unit = {},
    onHeatmapYearClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "统计", style = MaterialTheme.typography.displayLarge)
                Text(
                    text = "见证你的坚持与成长",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 新增：核心数据概览
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InfoItem(label = "总日记", value = "412", unit = "篇")
                    VerticalDivider(modifier = Modifier.height(32.dp).align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoItem(label = "累计字数", value = "12.8", unit = "k")
                    VerticalDivider(modifier = Modifier.height(32.dp).align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoItem(label = "本年字数", value = "4.2", unit = "k")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                summaryCards.forEach { card ->
                    StatsSummaryCard(summary = card, modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            MonthlyTrendCard(
                currentMonthLabel = "2026年5月",
                monthlyTotal = 12,
                dailyRecords = trendValues,
                onMonthClick = onTrendMonthClick
            )
        }

        item {
            HeatmapCard(
                currentYearLabel = "2026年",
                levels = heatmapLevels,
                onYearClick = onHeatmapYearClick
            )
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp, start = 2.dp))
        }
    }
}

@Composable
private fun StatsSummaryCard(summary: StatsSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = summary.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(text = summary.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = summary.value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthlyTrendCard(currentMonthLabel: String, monthlyTotal: Int, dailyRecords: List<Int>, onMonthClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "月度趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PeriodChip(label = currentMonthLabel, onClick = onMonthClick)
            }
            TrendChart(records = dailyRecords)
        }
    }
}

@Composable
private fun TrendChart(records: List<Int>) {
    val chartHeight = 100.dp
    Column {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            records.forEach { value ->
                val barHeight = (value * 10).coerceAtMost(100).dp
                Box(
                    modifier = Modifier.weight(1f).height(if (barHeight > 4.dp) barHeight else 4.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("15日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("31日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatmapCard(currentYearLabel: String, levels: List<List<Int>>, onYearClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "年度热力图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PeriodChip(label = currentYearLabel, onClick = onYearClick)
            }
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                HeatmapGrid(levels = levels)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(HeatLevelEmpty, HeatLevelLight, HeatLevelMedium, HeatLevelStrong).forEach { color ->
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(color))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeatmapGrid(levels: List<List<Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        levels.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { level ->
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(heatmapColor(level)))
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Icon(Icons.Outlined.ArrowDropDown, null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun heatmapColor(level: Int): Color {
    return when (level) {
        3 -> Color(0xFF17834F)
        2 -> Color(0xFF8DD27A)
        1 -> Color(0xFFCDEFC5)
        else -> Color(0xFFF0F2EF)
    }
}

private data class StatsSummary(val icon: ImageVector, val title: String, val value: String)
private val summaryCards = listOf(
    StatsSummary(Icons.Outlined.CalendarMonth, "本月记录", "12天"),
    StatsSummary(Icons.Outlined.LocalFireDepartment, "连续记录", "5天"),
    StatsSummary(Icons.Outlined.BarChart, "全年记录", "126天")
)
private val trendValues = listOf(0,0,0,0,5,7,0,3,4,0,2,7,4,0,2,4,0,3,6,0,4,0,3,0,7,0,3,0,5,0,4)
private val heatmapLevels = List(7) { List(52) { (0..3).random() } }
private val HeatLevelEmpty = Color(0xFFF0F2EF)
private val HeatLevelLight = Color(0xFFCDEFC5)
private val HeatLevelMedium = Color(0xFF8DD27A)
private val HeatLevelStrong = Color(0xFF17834F)
