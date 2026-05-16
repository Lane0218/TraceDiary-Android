package com.tracediary.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "回顾", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(bottom = 16.dp))
            
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf("日历", "年度总结", "搜索")
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> CalendarReviewContent()
                1 -> AnnualSummaryContent()
                2 -> SearchContent()
            }
        }
    }
}

@Composable
private fun CalendarReviewContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}) { Icon(Icons.Outlined.ChevronLeft, null) }
                        Text("2026年5月", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = {}) { Icon(Icons.Outlined.ChevronRight, null) }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val days = listOf("日", "一", "二", "三", "四", "五", "六")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        days.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 修复日历逻辑：2026年5月 1号是周五，31号是周日
                    // 填充空白
                    val emptyDays = 5 
                    val totalDays = 31
                    val dateItems = (1..totalDays).map { it.toString() }
                    
                    val allCells = List(emptyDays) { "" } + dateItems
                    allCells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            week.forEach { date ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (date == "15") MaterialTheme.colorScheme.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date.isNotEmpty()) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = date,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (date == "15") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (date == "15") FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (date.toInt() < 20 && date.toInt() % 4 == 0) {
                                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (date == "15") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                                            }
                                        }
                                    }
                                }
                            }
                            // 补足最后一周的空格
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
            Text(text = "5月15日 星期五", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Text(
                        text = "往年今日",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    listOf("2025年5月15日", "2024年5月15日", "2023年5月15日").forEachIndexed { index, date ->
                        ListItem(
                            headlineContent = { Text(date) },
                            leadingContent = { Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                            modifier = Modifier.clickable { }
                        )
                        if (index < 2) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualSummaryContent() {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(text = "年度总结", style = MaterialTheme.typography.titleLarge)
        }
        items((2023..2026).reversed().toList()) { year ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "${year}年度总结", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
private fun SearchContent() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索日记内容...") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            shape = MaterialTheme.shapes.medium
        )
        Text(text = "搜索建议", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = {}, label = { Text("去年今日") })
            SuggestionChip(onClick = {}, label = { Text("快乐") })
            SuggestionChip(onClick = {}, label = { Text("旅行") })
        }
    }
}
