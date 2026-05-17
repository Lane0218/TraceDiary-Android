package com.tracediary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            Text(text = "统计", style = MaterialTheme.typography.displayLarge)
        }

        // 统一的 Bento Box 概览网格
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val row1 = listOf(
                    StatsData("总日记", "412", "篇", Icons.Outlined.Description),
                    StatsData("累计字数", "12.8", "k", Icons.Outlined.TextFormat),
                    StatsData("本年字数", "4.2", "k", Icons.Outlined.EditCalendar)
                )
                val row2 = listOf(
                    StatsData("本月记录", "12", "天", Icons.Outlined.CalendarMonth),
                    StatsData("连续记录", "5", "天", Icons.Outlined.LocalFireDepartment),
                    StatsData("全年记录", "126", "天", Icons.Outlined.BarChart)
                )
                DataGridRow(row1)
                DataGridRow(row2)
            }
        }

        item {
            MonthlyTrendCard(
                title = "字数趋势",
                currentLabel = "2026年",
                data = yearlyWordCountData,
                onYearClick = onTrendMonthClick
            )
        }

        item {
            HeatmapCard(
                currentYearLabel = "2026年",
                onYearClick = onHeatmapYearClick
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
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
            Row(modifier = Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                data.forEach { (_, count) ->
                    Box(modifier = Modifier.weight(1f).height((100 * (count.toFloat() / maxVal)).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                data.forEachIndexed { index, (month, _) -> if (index % 2 == 0) Text(text = month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun HeatmapCard(currentYearLabel: String, onYearClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "年度热力图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PeriodChip(label = currentYearLabel, onClick = onYearClick)
            }

            // 重构：采用月份微缩方阵 (4排 x 3列)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (col in 0 until 3) {
                            val month = row * 3 + col + 1
                            MiniMonthGrid(monthName = "${month}月", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "未记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFF0F2EF)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "已记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun MiniMonthGrid(monthName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = monthName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
        // 模拟月历格子 (5行 x 7列)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (w in 0 until 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (d in 0 until 7) {
                        val isRecorded = (0..10).random() > 6
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (isRecorded) MaterialTheme.colorScheme.primary else Color(0xFFF4F6F3))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.clip(CircleShape).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelSmall); Icon(Icons.Outlined.ArrowDropDown, null, modifier = Modifier.size(14.dp))
        }
    }
}

private data class StatsData(val label: String, val value: String, val unit: String, val icon: ImageVector)
private val yearlyWordCountData = listOf("25/6" to 1200, "25/7" to 1500, "25/8" to 900, "25/9" to 2100, "25/10" to 1800, "25/11" to 2400, "25/12" to 3000, "26/1" to 1400, "26/2" to 1100, "26/3" to 1900, "26/4" to 2200, "26/5" to 2500)
