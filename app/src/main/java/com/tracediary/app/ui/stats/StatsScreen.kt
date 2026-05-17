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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            }
        }

        // 统一的 Bento Box 概览网格 (2x3)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BentoCard(label = "总日记", value = "412", unit = "篇", modifier = Modifier.weight(1f))
                    BentoCard(label = "累计字数", value = "12.8", unit = "k", modifier = Modifier.weight(1f))
                    BentoCard(label = "本年字数", value = "4.2", unit = "k", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BentoIconCard(icon = Icons.Outlined.CalendarMonth, label = "本月记录", value = "12天", modifier = Modifier.weight(1f))
                    BentoIconCard(icon = Icons.Outlined.LocalFireDepartment, label = "连续记录", value = "5天", modifier = Modifier.weight(1f))
                    BentoIconCard(icon = Icons.Outlined.BarChart, label = "全年记录", value = "126天", modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            MonthlyTrendCard(
                title = "字数趋势 (近12个月)",
                currentLabel = "2026年",
                data = yearlyWordCountData,
                onYearClick = onTrendMonthClick
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
private fun BentoCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp, start = 2.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BentoIconCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthlyTrendCard(title: String, currentLabel: String, data: List<Pair<String, Int>>, onYearClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PeriodChip(label = currentLabel, onClick = onYearClick)
            }
            
            val maxVal = data.maxOf { it.second }.coerceAtLeast(1)
            val chartHeight = 100.dp
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(chartHeight),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    data.forEach { (month, count) ->
                        val barHeight = (chartHeight.value * (count.toFloat() / maxVal)).dp
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (barHeight > 4.dp) barHeight else 4.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.forEachIndexed { index, (month, _) ->
                        if (index % 2 == 0) {
                            Text(text = month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
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

private val yearlyWordCountData = listOf(
    "25/6" to 1200, "25/7" to 1500, "25/8" to 900, "25/9" to 2100, "25/10" to 1800, "25/11" to 2400,
    "25/12" to 3000, "26/1" to 1400, "26/2" to 1100, "26/3" to 1900, "26/4" to 2200, "26/5" to 2500
)

private val heatmapLevels = List(7) { List(52) { (0..3).random() } }
private val HeatLevelEmpty = Color(0xFFF0F2EF)
private val HeatLevelLight = Color(0xFFCDEFC5)
private val HeatLevelMedium = Color(0xFF8DD27A)
private val HeatLevelStrong = Color(0xFF17834F)
