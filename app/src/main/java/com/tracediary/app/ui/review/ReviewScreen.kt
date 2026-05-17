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
            Text(text = "回顾", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(bottom = 12.dp))
            
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                val labels = listOf("日历", "年度总结", "搜索")
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                        icon = {}, // 移除默认的勾选图标
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.surface,
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = SegmentedButtonDefaults.border(color = Color.Transparent)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronLeft, null, modifier = Modifier.size(20.dp)) }
                        Text("2026年5月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(20.dp)) }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val days = listOf("日", "一", "二", "三", "四", "五", "六")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        days.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val emptyDays = 5 
                    val totalDays = 31
                    val dateItems = (1..totalDays).map { it.toString() }
                    val allCells = List(emptyDays) { "" } + dateItems
                    
                    allCells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            week.forEach { date ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.2f)
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
                                            if (date.isNotEmpty() && date.toInt() < 16 && date.toInt() % 4 == 0) {
                                                Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (date == "15") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                                            }
                                        }
                                    }
                                }
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
            Text(text = "5月15日 星期五", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Text(
                        text = "往年今日",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    listOf("2025年5月15日", "2024年5月15日", "2023年5月15日").forEachIndexed { index, date ->
                        ListItem(
                            headlineContent = { Text(date, style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.clickable { }
                        )
                        if (index < 2) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun AnnualSummaryContent() {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(text = "年度总结", style = MaterialTheme.typography.titleMedium)
        }
        items((2023..2026).reversed().toList()) { year ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "${year}年度总结", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
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
            placeholder = { Text("搜索日记内容...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(20.dp)) },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(text = "最近搜索", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "暂无搜索记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}
