package com.tracediary.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
fun TodayScreen(
    modifier: Modifier = Modifier
) {
    val diaryContent = "今天是一个安静而充实的日子。早上按计划完成了工作，效率比昨天高一些，处理了几个积压已久的任务，心里轻松了不少。午后去附近的公园走了走，阳光透过树叶洒在地上，微风正好，整个人都放松下来。晚上读了几页书，收获了一些新的想法，准备明天实践一下。希望保持这样的节奏，一步一步靠近自己的目标。"
    val wordCount = diaryContent.length

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "今天", style = MaterialTheme.typography.displayLarge)
                IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, null) }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronLeft, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                        Text(text = "2026年5月", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
                        val dates = listOf(11, 12, 13, 14, 15, 16, 17)
                        dates.forEachIndexed { index, date ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(if (date == 15) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = weekDays[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (date == 15) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = date.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (date == 15) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (date == 15) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "5月15日 星期五", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = diaryContent,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${wordCount} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "已同步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            Text(text = "往年今日", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }

        items(listOf("2025年5月15日", "2024年5月15日")) { date ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = date, style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
