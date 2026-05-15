package com.tracediary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracediary.app.ui.theme.TraceDiaryTheme

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onTrendMonthClick: () -> Unit = {},
    onHeatmapYearClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StatsBackground),
        contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "统计",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor
                )
                Text(
                    text = "记录生活点滴，见证你的坚持与成长",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = BodyColor
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                summaryCards.forEach { card ->
                    StatsSummaryCard(
                        summary = card,
                        modifier = Modifier.weight(1f)
                    )
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
private fun StatsSummaryCard(
    summary: StatsSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 198.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BadgeMonogram(
                icon = summary.icon,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = summary.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = BodyColor
            )

            Text(
                text = summary.value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccentColor
            )

            Text(
                text = summary.caption,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = SecondaryTextColor
            )
        }
    }
}

@Composable
private fun MonthlyTrendCard(
    currentMonthLabel: String,
    monthlyTotal: Int,
    dailyRecords: List<Int>,
    onMonthClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "月度趋势",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor
                )
                PeriodChip(
                    label = currentMonthLabel,
                    onClick = onMonthClick
                )
            }

            Text(
                text = buildAnnotatedString {
                    append("本月记录 ")
                    pushStyle(SpanStyle(color = AccentColor, fontWeight = FontWeight.SemiBold))
                    append("$monthlyTotal")
                    pop()
                    append(" 天")
                },
                fontSize = 16.sp,
                color = BodyColor
            )

            TrendChart(records = dailyRecords)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem(label = "已记录", color = AccentColor)
                Spacer(modifier = Modifier.width(28.dp))
                LegendItem(label = "未记录", color = PlaceholderBarColor)
            }
        }
    }
}

@Composable
private fun TrendChart(records: List<Int>) {
    val chartHeight = 228.dp
    val tickLabels = listOf("5/1", "5/6", "5/11", "5/16", "5/21", "5/26", "5/31")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .height(chartHeight)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            listOf("8天", "6天", "4天", "2天", "0天").forEach { label ->
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = SecondaryTextColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(5) {
                        HorizontalDivider(
                            color = DividerColor,
                            thickness = 1.dp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    records.forEach { value ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(PlaceholderBarColor)
                            )
                            if (value > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .height((value * 26).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(AccentColor)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tickLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = SecondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(
    currentYearLabel: String,
    levels: List<List<Int>>,
    onYearClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "年度热力图",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor
                )
                PeriodChip(
                    label = currentYearLabel,
                    onClick = onYearClick
                )
            }

            HeatmapGrid(levels = levels)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(label = "16天以上", color = HeatLevelStrong)
                LegendItem(label = "8-15天", color = HeatLevelMedium)
                LegendItem(label = "1-7天", color = HeatLevelLight)
                LegendItem(label = "无记录", color = HeatLevelEmpty)
            }
        }
    }
}

@Composable
private fun HeatmapGrid(levels: List<List<Int>>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.width(34.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.height(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = SecondaryTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    monthLabels.forEach { label ->
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = SecondaryTextColor
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { level ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(heatmapColor(level))
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
private fun PeriodChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = TitleColor
            )
            ChevronIcon()
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = SecondaryTextColor
        )
    }
}

@Composable
private fun BadgeMonogram(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(BadgeBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = AccentColor,
        )
    }
}

@Composable
private fun ChevronIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .padding(top = 1.dp)
    ) {
        Text(
            text = "⌄",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 12.sp,
            color = SecondaryTextColor
        )
    }
}

private fun heatmapColor(level: Int): Color = when (level) {
    3 -> HeatLevelStrong
    2 -> HeatLevelMedium
    1 -> HeatLevelLight
    else -> HeatLevelEmpty
}

private data class StatsSummary(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val caption: String
)

private val summaryCards = listOf(
    StatsSummary(
        icon = Icons.Outlined.CalendarMonth,
        title = "本月记录",
        value = "12天",
        caption = "本月记录天数"
    ),
    StatsSummary(
        icon = Icons.Outlined.LocalFireDepartment,
        title = "连续记录",
        value = "5天",
        caption = "当前连续天数"
    ),
    StatsSummary(
        icon = Icons.Outlined.BarChart,
        title = "全年记录",
        value = "126天",
        caption = "年度记录天数"
    )
)

private val trendValues = listOf(
    0, 0, 0, 0, 5, 7, 0, 3, 4, 0, 2, 7, 4, 0, 2, 4, 0, 3, 6, 0, 4, 0, 3, 0, 7, 0, 3, 0, 5, 0, 4
)

private val heatmapLevels = listOf(
    listOf(1, 2, 2, 1, 1, 2, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 2, 1, 2, 2, 1, 2, 1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 2, 1, 1, 2, 1, 1, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 2, 2, 1, 1, 2, 1, 1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 1, 1, 2, 2, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 1, 2, 1, 2, 1, 1, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(1, 1, 1, 2, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
)

private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val monthLabels = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")

private val StatsBackground = Color(0xFFF4F6F3)
private val CardColor = Color(0xFFFCFCFA)
private val TitleColor = Color(0xFF101A16)
private val BodyColor = Color(0xFF677173)
private val SecondaryTextColor = Color(0xFF7A8488)
private val DividerColor = Color(0xFFE3E7E4)
private val AccentColor = Color(0xFF0E7A50)
private val BadgeBackground = Color(0xFFEAF3EE)
private val PlaceholderBarColor = Color(0xFFE8EAE8)
private val HeatLevelEmpty = Color(0xFFF0F2EF)
private val HeatLevelLight = Color(0xFFCDEFC5)
private val HeatLevelMedium = Color(0xFF8DD27A)
private val HeatLevelStrong = Color(0xFF17834F)

@Preview(showBackground = true, backgroundColor = 0xFFF4F6F3, widthDp = 393, heightDp = 852)
@Composable
private fun StatsScreenPreview() {
    TraceDiaryTheme {
        StatsScreen()
    }
}
