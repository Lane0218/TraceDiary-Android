package com.tracediary.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracediary.app.ui.theme.TraceDiaryTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val weekDates = remember { sampleWeekDates() }
    var selectedEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.of(2026, 5, 15).toEpochDay())
    }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }
    val entry = remember(selectedDate) { sampleTodayEntry(selectedDate) }

    var content by rememberSaveable(selectedEpochDay) { mutableStateOf(entry.content) }
    var saveState by rememberSaveable(selectedEpochDay) { mutableStateOf(TodaySaveState.Synced) }

    LaunchedEffect(content, selectedEpochDay) {
        if (content != entry.content) {
            saveState = TodaySaveState.Saving
            delay(500)
            saveState = TodaySaveState.Synced
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TodayPalette.ScreenBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp,
            top = 28.dp,
            end = 24.dp,
            bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            TodayTopBar()
        }
        item {
            TodayCard {
                WeekCalendar(
                    dates = weekDates,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedEpochDay = it.toEpochDay() }
                )
            }
        }
        item {
            Text(
                text = selectedDate.format(todayTitleFormatter),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = TodayPalette.PrimaryText
                )
            )
        }
        item {
            TodayEditorCard(
                content = content,
                onContentChange = { content = it },
                status = saveState
            )
        }
        item {
            Text(
                text = "往年今日",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TodayPalette.PrimaryText
                )
            )
        }
        items(entry.previousYears) { previous ->
            TodayHistoryRow(dateText = previous)
        }
    }
}

@Composable
private fun TodayTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TodayPalette.PrimaryText
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SearchIcon()
            OverflowIcon()
        }
    }
}

@Composable
private fun WeekCalendar(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .clickable { onDateSelected(date) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekdayShortLabel(date),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = TodayPalette.SecondaryText
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(TodayPalette.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TodayPalette.Accent)
                    )
                } else {
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = TodayPalette.PrimaryText
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayEditorCard(
    content: String,
    onContentChange: (String) -> Unit,
    status: TodaySaveState
) {
    TodayCard {
        Column {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp),
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Normal,
                    color = TodayPalette.PrimaryText
                ),
                cursorBrush = SolidColor(TodayPalette.Accent)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusCheckIcon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = TodayPalette.TertiaryText
                    )
                )
            }
        }
    }
}

@Composable
private fun TodayHistoryRow(dateText: String) {
    TodayCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 28.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TodayPalette.PrimaryText
                )
            )
            ChevronIcon()
        }
    }
}

@Composable
private fun TodayCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = TodayPalette.CardShadow,
                spotColor = TodayPalette.CardShadow
            )
            .clip(RoundedCornerShape(26.dp))
            .background(TodayPalette.CardBackground)
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun SearchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.4.dp.toPx()
        val radius = size.minDimension * 0.34f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(
            color = TodayPalette.PrimaryText,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = TodayPalette.PrimaryText,
            start = Offset(size.width * 0.68f, size.height * 0.68f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun OverflowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val radius = size.minDimension * 0.08f
        val x = size.width / 2f
        listOf(0.2f, 0.5f, 0.8f).forEach { fraction ->
            drawCircle(
                color = TodayPalette.PrimaryText,
                radius = radius,
                center = Offset(x, size.height * fraction)
            )
        }
    }
}

@Composable
private fun ChevronIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = TodayPalette.IconMuted,
            start = Offset(size.width * 0.28f, size.height * 0.18f),
            end = Offset(size.width * 0.72f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = TodayPalette.IconMuted,
            start = Offset(size.width * 0.28f, size.height * 0.82f),
            end = Offset(size.width * 0.72f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun StatusCheckIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = TodayPalette.Accent,
            radius = size.minDimension * 0.42f,
            center = center,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = TodayPalette.Accent,
            start = Offset(size.width * 0.3f, size.height * 0.53f),
            end = Offset(size.width * 0.45f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = TodayPalette.Accent,
            start = Offset(size.width * 0.45f, size.height * 0.68f),
            end = Offset(size.width * 0.72f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private data class TodayEntry(
    val content: String,
    val previousYears: List<String>
)

private enum class TodaySaveState(val label: String) {
    Saving("保存中"),
    Synced("已保存 · 云同步完成")
}

private object TodayPalette {
    val ScreenBackground = Color(0xFFF5F6F2)
    val CardBackground = Color(0xFFFCFCFA)
    val CardShadow = Color(0x14000000)
    val Accent = Color(0xFF0F7A45)
    val PrimaryText = Color(0xFF161A17)
    val SecondaryText = Color(0xFF7B7F79)
    val TertiaryText = Color(0xFF8E938E)
    val IconMuted = Color(0xFFACB0AB)
}

private val chineseLocale: Locale = Locale.SIMPLIFIED_CHINESE
private val todayTitleFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", chineseLocale)

private fun sampleWeekDates(): List<LocalDate> {
    val start = LocalDate.of(2026, 5, 10)
    return List(7) { offset -> start.plusDays(offset.toLong()) }
}

private fun sampleTodayEntry(date: LocalDate): TodayEntry {
    val monthDay = DateTimeFormatter.ofPattern("M月d日", chineseLocale).format(date)
    return when (date.dayOfMonth) {
        11 -> TodayEntry(
            content = "今天有点慢下来，上午把积压的邮件处理完，下午把下周的安排重新排了一遍。\n\n晚上和家里人通了电话，心里踏实了不少，也更确定接下来要把节奏稳住。",
            previousYears = listOf("2025年$monthDay", "2024年$monthDay")
        )
        12 -> TodayEntry(
            content = "上午出门见了客户，沟通比预期顺利。回来的路上顺手记下几个需要继续跟进的点。\n\n晚上没有再塞新的任务，留了一点空白时间给自己，状态反而更稳定。",
            previousYears = listOf("2025年$monthDay")
        )
        13 -> TodayEntry(
            content = "今天主要在收尾一个阶段任务。虽然节奏紧，但没有慌，按优先级一件件推进下去，最后结果还不错。\n\n临睡前把明天要做的事情写下来，脑子就安静了。",
            previousYears = listOf("2024年$monthDay", "2023年$monthDay")
        )
        14 -> TodayEntry(
            content = "下午开会时间偏长，原本的计划被切碎了一些。不过好在关键节点还是推进了。\n\n晚上散步二十分钟，回来后把思路重新梳理了一遍，明天会更顺。",
            previousYears = listOf("2025年$monthDay", "2022年$monthDay")
        )
        15 -> TodayEntry(
            content = "今天是一个安静而充实的日子。\n\n早上按计划完成了工作，效率比昨天高一些，处理了几个积压已久的任务，心里轻松了不少。\n\n午后去附近的公园走了走，阳光透过树叶洒在地上，微风正好，整个人都放松下来。\n\n晚上读了几页书，收获了一些新的想法，准备明天实践一下。\n\n希望保持这样的节奏，一步一步靠近自己的目标。",
            previousYears = listOf("2025年$monthDay", "2024年$monthDay")
        )
        16 -> TodayEntry(
            content = "今天把一个拖了很久的小问题彻底解决了，虽然不大，但完成之后心情明显轻了。\n\n晚上没有继续加码，而是早点休息，给明天留一点余地。",
            previousYears = listOf("2025年$monthDay", "2024年$monthDay", "2023年$monthDay")
        )
        else -> TodayEntry(
            content = "今天的节奏比较平稳，完成了手头最重要的几件事，也给自己留出一点整理和复盘的时间。",
            previousYears = listOf("2025年$monthDay")
        )
    }
}

private fun weekdayShortLabel(date: LocalDate): String = when (date.dayOfWeek.value % 7) {
    0 -> "日"
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    else -> "六"
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6F2)
@Composable
private fun TodayScreenPreview() {
    TraceDiaryTheme {
        TodayScreen()
    }
}
