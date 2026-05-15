package com.tracediary.app.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracediary.app.ui.theme.TraceDiaryTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    var selectedSection by rememberSaveable { mutableStateOf(ReviewSection.Calendar) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ReviewPalette.ScreenBackground),
        contentPadding = PaddingValues(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "回顾",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReviewPalette.PrimaryText
                )
            )
        }
        item {
            SegmentedTabs(
                options = ReviewSection.entries.toList(),
                selected = selectedSection,
                onSelected = { selectedSection = it }
            )
        }
        when (selectedSection) {
            ReviewSection.Calendar -> calendarSection()
            ReviewSection.YearSummary -> yearSummarySection()
            ReviewSection.Search -> searchSection()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.calendarSection() {
    item {
        CalendarPane()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.yearSummarySection() {
    item {
        YearSummaryPane()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchSection() {
    item {
        SearchPane()
    }
}

@Composable
private fun CalendarPane() {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val displayedMonth = remember(monthOffset) { YearMonth.of(2026, 5).plusMonths(monthOffset.toLong()) }
    val monthDates = remember(displayedMonth) { buildMonthGrid(displayedMonth) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.of(2026, 5, 15).toEpochDay()) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }

    ReviewCard(contentPadding = PaddingValues(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArrowButton(direction = ArrowDirection.Left) { monthOffset -= 1 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayedMonth.format(monthFormatter),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = ReviewPalette.PrimaryText
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    DropDownChevron()
                }
                ArrowButton(direction = ArrowDirection.Right) { monthOffset += 1 }
            }

            WeekHeader()
            monthDates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    week.forEach { cell ->
                        CalendarDayCell(
                            date = cell,
                            inMonth = cell.month == displayedMonth.month,
                            selected = cell == selectedDate,
                            hasEntry = sampleRecordedDates.contains(cell),
                            onClick = { selectedEpochDay = cell.toEpochDay() }
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    ReviewCard {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = selectedDate.format(reviewDayTitleFormatter),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReviewPalette.PrimaryText
                )
            )
            ActionRow(
                leading = { DocumentIcon() },
                title = "查看当天记录"
            )
            DividerLine()
            SectionAccentTitle(text = "往年今日")
            samplePreviousYears(selectedDate).forEach { dateText ->
                ActionRow(
                    leading = { CalendarIcon() },
                    title = dateText
                )
            }
        }
    }
}

@Composable
private fun YearSummaryPane() {
    var selectedYear by rememberSaveable { mutableIntStateOf(2026) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var content by rememberSaveable(selectedYear) {
        mutableStateOf(sampleYearSummaryContent(selectedYear))
    }
    var saveState by rememberSaveable(selectedYear) {
        mutableStateOf(YearSummarySaveState.Synced)
    }

    LaunchedEffect(content, selectedYear, editing) {
        if (editing && content != sampleYearSummaryContent(selectedYear)) {
            saveState = YearSummarySaveState.Saving
            delay(500)
            saveState = YearSummarySaveState.Synced
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapsuleButton(
                text = "${selectedYear}年",
                trailing = { DropDownChevron(color = ReviewPalette.SecondaryText) },
                onClick = {
                    selectedYear = when (selectedYear) {
                        2026 -> 2025
                        2025 -> 2024
                        2024 -> 2023
                        else -> 2026
                    }
                }
            )
            TextAction(
                text = if (editing) "完成" else "编辑",
                icon = { EditIcon() },
                onClick = { editing = !editing }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedYear}年年度总结",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReviewPalette.PrimaryText
                )
            )
            SaveStatus(status = saveState)
        }

        ReviewCard {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (editing) {
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 440.dp),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            lineHeight = 34.sp,
                            color = ReviewPalette.PrimaryText
                        ),
                        cursorBrush = SolidColor(ReviewPalette.Accent)
                    )
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 34.sp,
                            color = ReviewPalette.PrimaryText
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${content.length}/2000",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp,
                            color = ReviewPalette.TertiaryText
                        )
                    )
                }
            }
        }

        Text(
            text = "历年总结",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ReviewPalette.PrimaryText
            )
        )

        ReviewCard(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) {
            Column {
                listOf(2025, 2024, 2023).forEachIndexed { index, year ->
                    ActionRow(
                        leading = {},
                        title = "${year}年年度总结",
                        compact = true
                    )
                    if (index != 2) {
                        DividerLine(modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPane() {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    val results = remember(query, selectedFilter) {
        sampleSearchEntries.filter { entry ->
            val matchesQuery = query.isBlank() ||
                entry.dateText.contains(query) ||
                entry.preview.contains(query) ||
                entry.weekday.contains(query)
            val matchesFilter = when (selectedFilter) {
                SearchFilter.All -> true
                SearchFilter.ThisYear -> entry.date.year == 2026
                SearchFilter.RecordedDate -> entry.date.year <= 2026
            }
            matchesQuery && matchesFilter
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索记录内容"
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchFilter.entries.forEach { filter ->
                FilterChip(
                    text = filter.label,
                    selected = filter == selectedFilter,
                    trailingCalendar = filter == SearchFilter.RecordedDate,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共找到 ${results.size} 条记录",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = ReviewPalette.SecondaryText
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "按日期降序",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = ReviewPalette.SecondaryText
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                DropDownChevron(color = ReviewPalette.SecondaryText)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            results.forEach { entry ->
                SearchResultCard(entry = entry)
            }
        }

        if (query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchIcon(tint = ReviewPalette.IconMuted)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "输入关键词查找过去的记录",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = ReviewPalette.IconMuted
                    )
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabs(
    options: List<ReviewSection>,
    selected: ReviewSection,
    onSelected: (ReviewSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = ReviewPalette.CardShadow,
                spotColor = ReviewPalette.CardShadow
            )
            .clip(RoundedCornerShape(24.dp))
            .background(ReviewPalette.CardBackground)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { section ->
            val isSelected = section == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) ReviewPalette.TabSelected else Color.Transparent)
                    .clickable { onSelected(section) }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) ReviewPalette.Accent else ReviewPalette.SecondaryText
                    )
                )
            }
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.width(40.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = ReviewPalette.SecondaryText
                )
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    hasEntry: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(40.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ReviewPalette.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        } else {
            Text(
                text = "${date.dayOfMonth}",
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    color = if (inMonth) ReviewPalette.PrimaryText else ReviewPalette.OutOfMonthText
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        hasEntry && inMonth -> ReviewPalette.Accent
                        else -> Color.Transparent
                    }
                )
        )
    }
}

@Composable
private fun ActionRow(
    leading: @Composable () -> Unit,
    title: String,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 10.dp else 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(if (compact) 0.dp else 28.dp), contentAlignment = Alignment.Center) {
                leading()
            }
            if (!compact) {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (compact) 18.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = ReviewPalette.PrimaryText
                )
            )
        }
        ChevronIcon()
    }
}

@Composable
private fun SectionAccentTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReviewPalette.Accent)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ReviewPalette.PrimaryText
            )
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = ReviewPalette.CardShadow,
                spotColor = ReviewPalette.CardShadow
            )
            .clip(RoundedCornerShape(28.dp))
            .background(ReviewPalette.CardBackground)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIcon(tint = ReviewPalette.SecondaryText)
        Spacer(modifier = Modifier.width(16.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 18.sp,
                color = ReviewPalette.PrimaryText
            ),
            singleLine = true,
            cursorBrush = SolidColor(ReviewPalette.Accent),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                color = ReviewPalette.IconMuted
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    trailingCalendar: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) ReviewPalette.FilterSelected else ReviewPalette.CardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) ReviewPalette.Accent else ReviewPalette.SecondaryText
            )
        )
        if (trailingCalendar) {
            Spacer(modifier = Modifier.width(10.dp))
            CalendarIcon(tint = ReviewPalette.SecondaryText, iconSize = 18.dp)
        }
    }
}

@Composable
private fun SearchResultCard(entry: SearchEntry) {
    ReviewCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                CalendarIcon(iconSize = 24.dp)
                Spacer(modifier = Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.dateText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ReviewPalette.PrimaryText
                        )
                    )
                    Text(
                        text = entry.weekday,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = ReviewPalette.SecondaryText
                        )
                    )
                    Text(
                        text = entry.preview,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            color = ReviewPalette.SecondaryText
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            ChevronIcon()
        }
    }
}

@Composable
private fun CapsuleButton(
    text: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ReviewPalette.CardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ReviewPalette.PrimaryText
            )
        )
        trailing?.let {
            Spacer(modifier = Modifier.width(8.dp))
            it()
        }
    }
}

@Composable
private fun TextAction(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewPalette.Accent
            )
        )
    }
}

@Composable
private fun SaveStatus(status: YearSummarySaveState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusCheckIcon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                color = ReviewPalette.TertiaryText
            )
        )
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ReviewPalette.Divider)
    )
}

@Composable
private fun ReviewCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = ReviewPalette.CardShadow,
                spotColor = ReviewPalette.CardShadow
            )
            .clip(RoundedCornerShape(26.dp))
            .background(ReviewPalette.CardBackground)
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun ArrowButton(
    direction: ArrowDirection,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val stroke = 2.4.dp.toPx()
            if (direction == ArrowDirection.Left) {
                drawLine(
                    color = ReviewPalette.PrimaryText,
                    start = Offset(size.width * 0.72f, size.height * 0.18f),
                    end = Offset(size.width * 0.28f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ReviewPalette.PrimaryText,
                    start = Offset(size.width * 0.72f, size.height * 0.82f),
                    end = Offset(size.width * 0.28f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = ReviewPalette.PrimaryText,
                    start = Offset(size.width * 0.28f, size.height * 0.18f),
                    end = Offset(size.width * 0.72f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ReviewPalette.PrimaryText,
                    start = Offset(size.width * 0.28f, size.height * 0.82f),
                    end = Offset(size.width * 0.72f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun DropDownChevron(
    modifier: Modifier = Modifier,
    color: Color = ReviewPalette.PrimaryText
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.36f),
            end = Offset(size.width * 0.5f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.36f),
            end = Offset(size.width * 0.5f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ChevronIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = ReviewPalette.IconMuted,
            start = Offset(size.width * 0.28f, size.height * 0.18f),
            end = Offset(size.width * 0.72f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ReviewPalette.IconMuted,
            start = Offset(size.width * 0.28f, size.height * 0.82f),
            end = Offset(size.width * 0.72f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = ReviewPalette.PrimaryText
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.4.dp.toPx()
        val radius = size.minDimension * 0.34f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = radius, center = center, style = Stroke(width = stroke))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.68f, size.height * 0.68f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CalendarIcon(
    modifier: Modifier = Modifier,
    tint: Color = ReviewPalette.Accent,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val stroke = 2.1.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.66f),
            cornerRadius = CornerRadius(size.width * 0.12f, size.width * 0.12f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.28f, size.height * 0.08f),
            end = Offset(size.width * 0.28f, size.height * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.72f, size.height * 0.08f),
            end = Offset(size.width * 0.72f, size.height * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.16f, size.height * 0.38f),
            end = Offset(size.width * 0.84f, size.height * 0.38f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = size.minDimension * 0.05f, center = Offset(size.width * 0.38f, size.height * 0.56f))
        drawCircle(color = tint, radius = size.minDimension * 0.05f, center = Offset(size.width * 0.62f, size.height * 0.56f))
    }
}

@Composable
private fun DocumentIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.1.dp.toPx()
        drawRoundRect(
            color = ReviewPalette.Accent,
            topLeft = Offset(size.width * 0.2f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.76f),
            cornerRadius = CornerRadius(size.width * 0.14f, size.width * 0.14f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.34f, size.height * 0.38f),
            end = Offset(size.width * 0.66f, size.height * 0.38f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.34f, size.height * 0.56f),
            end = Offset(size.width * 0.58f, size.height * 0.56f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun EditIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.26f, size.height * 0.74f),
            end = Offset(size.width * 0.76f, size.height * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.64f, size.height * 0.2f),
            end = Offset(size.width * 0.8f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.2f, size.height * 0.82f),
            end = Offset(size.width * 0.46f, size.height * 0.82f),
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
            color = ReviewPalette.Accent,
            radius = size.minDimension * 0.42f,
            center = center,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.3f, size.height * 0.53f),
            end = Offset(size.width * 0.45f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ReviewPalette.Accent,
            start = Offset(size.width * 0.45f, size.height * 0.68f),
            end = Offset(size.width * 0.72f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private enum class ReviewSection(val label: String) {
    Calendar("日历"),
    YearSummary("年度总结"),
    Search("搜索")
}

private enum class SearchFilter(val label: String) {
    All("全部"),
    ThisYear("今年"),
    RecordedDate("有记录日期")
}

private enum class ArrowDirection {
    Left,
    Right
}

private enum class YearSummarySaveState(val label: String) {
    Saving("保存中"),
    Synced("已保存 · 云同步完成")
}

private data class SearchEntry(
    val date: LocalDate,
    val dateText: String,
    val weekday: String,
    val preview: String
)

private object ReviewPalette {
    val ScreenBackground = Color(0xFFF5F6F2)
    val CardBackground = Color(0xFFFCFCFA)
    val CardShadow = Color(0x14000000)
    val TabSelected = Color(0xFFF2F7F3)
    val FilterSelected = Color(0xFFF5FBF7)
    val Accent = Color(0xFF0F7A45)
    val PrimaryText = Color(0xFF161A17)
    val SecondaryText = Color(0xFF737973)
    val TertiaryText = Color(0xFF8F948F)
    val OutOfMonthText = Color(0xFFB6BAB5)
    val Divider = Color(0xFFE8ECE8)
    val IconMuted = Color(0xFFA6AAA5)
}

private val locale: Locale = Locale.SIMPLIFIED_CHINESE
private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", locale)
private val reviewDayTitleFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", locale)
private val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

private val sampleRecordedDates = setOf(
    LocalDate.of(2026, 5, 1),
    LocalDate.of(2026, 5, 2),
    LocalDate.of(2026, 5, 4),
    LocalDate.of(2026, 5, 5),
    LocalDate.of(2026, 5, 7),
    LocalDate.of(2026, 5, 8),
    LocalDate.of(2026, 5, 9),
    LocalDate.of(2026, 5, 10),
    LocalDate.of(2026, 5, 11),
    LocalDate.of(2026, 5, 12),
    LocalDate.of(2026, 5, 13),
    LocalDate.of(2026, 5, 14),
    LocalDate.of(2026, 5, 15),
    LocalDate.of(2026, 5, 16),
    LocalDate.of(2026, 5, 17),
    LocalDate.of(2026, 5, 18),
    LocalDate.of(2026, 5, 20),
    LocalDate.of(2026, 5, 22),
    LocalDate.of(2026, 5, 24),
    LocalDate.of(2026, 5, 25),
    LocalDate.of(2026, 5, 28),
    LocalDate.of(2026, 5, 29),
    LocalDate.of(2026, 5, 31)
)

private val sampleSearchEntries = listOf(
    SearchEntry(
        date = LocalDate.of(2026, 5, 12),
        dateText = "2026年5月12日",
        weekday = "星期二",
        preview = "项目推进顺利，和团队一起解决了几个关键问题，接下来准备把节奏再稳一稳。"
    ),
    SearchEntry(
        date = LocalDate.of(2025, 12, 28),
        dateText = "2025年12月28日",
        weekday = "星期日",
        preview = "整理了一些读书笔记，收获很大。晚上和朋友聊天，心情也明朗起来。"
    ),
    SearchEntry(
        date = LocalDate.of(2025, 9, 3),
        dateText = "2025年9月3日",
        weekday = "星期三",
        preview = "今天完成了阶段性目标，给自己一个小奖励，继续保持清醒和专注。"
    ),
    SearchEntry(
        date = LocalDate.of(2024, 7, 3),
        dateText = "2024年7月3日",
        weekday = "星期三",
        preview = "早起跑步，状态不错。白天专注工作，效率比昨天高一些。"
    ),
    SearchEntry(
        date = LocalDate.of(2024, 3, 18),
        dateText = "2024年3月18日",
        weekday = "星期一",
        preview = "学习了新的知识点，做了总结。计划周末去图书馆看书。"
    )
)

private fun buildMonthGrid(month: YearMonth): List<LocalDate> {
    val firstDate = month.atDay(1)
    val start = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val lastDate = month.atEndOfMonth()
    val end = lastDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val totalDays = start.datesUntil(end.plusDays(1)).toList()
    return if (totalDays.size >= 35) totalDays else totalDays + List(35 - totalDays.size) { end.plusDays((it + 1).toLong()) }
}

private fun samplePreviousYears(date: LocalDate): List<String> {
    val monthDay = DateTimeFormatter.ofPattern("M月d日", locale).format(date)
    return listOf("2025年$monthDay", "2024年$monthDay", "2023年$monthDay")
}

private fun sampleYearSummaryContent(year: Int): String = when (year) {
    2025 -> "这一年，我开始更认真地对待记录和复盘。工作上虽然有起伏，但逐渐建立了自己的节奏，很多事情不再依赖临时冲刺，而是靠稳定推进慢慢完成。\n\n生活上也更关注身体和情绪，开始给自己留白，不再让每一天都塞满任务。这样的变化不算激烈，却很真实。"
    2024 -> "这一年像是在重新校准方向。尝试了不少新方法，也踩过一些坑，但正因为如此，慢慢知道自己真正适合什么。\n\n和重要的人相处更耐心了，也学会在忙碌的时候给自己一点缓冲。"
    2023 -> "这一年有不少不确定，但也因此留下了很多值得记住的片段。很多选择当时看起来普通，回头看却构成了后来的转折。\n\n我希望以后仍然保持记录，因为它让我更清楚自己是怎么一步步走过来的。"
    else -> "这一年，我在节奏与心态上都有了明显的变化。学会了在忙碌中建立秩序，不再追求面面俱到，而是更专注于真正重要的事情。作息更规律，运动成了习惯，情绪也比过去更稳定。与家人和朋友的相处更加用心，留下了不少温暖的回忆。\n\n这一年，我完成了几个重要的目标。工作上推进了核心项目，积累了新的经验；持续学习，读完了几本对我启发很大的书，也尝试把知识应用到实践中；坚持记录和复盘，帮助我更好地理解自己，做出更清晰的选择。\n\n当然也有遗憾。比如有些计划因为拖延没有完成；在时间管理上仍有提升空间；偶尔被外界干扰，偏离了自己的节奏。这些提醒我，专注与自律需要持续练习，也要学会对自己更宽容。\n\n展望明年，我希望继续保持健康的生活与稳定的节奏。在工作上深耕专业能力，承担更多责任；在生活中，留出更多时间陪伴重要的人，去见识更广阔的世界；同时，坚持记录与复盘，让每一天都比昨天更靠近理想的自己。"
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6F2)
@Composable
private fun ReviewScreenPreview() {
    TraceDiaryTheme {
        ReviewScreen()
    }
}
