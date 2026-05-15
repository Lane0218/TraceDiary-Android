package com.tracediary.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracediary.app.ui.theme.TraceDiaryTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSyncSettingsClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onEditorSettingsClick: () -> Unit = {},
    onReminderSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsBackground)
    ) {
        val compact = maxWidth < 360.dp
        val horizontalPadding = if (compact) 18.dp else 24.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = if (compact) 26.dp else 32.dp,
                end = horizontalPadding,
                bottom = if (compact) 26.dp else 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 22.dp)
        ) {
            item {
                Text(
                    text = "设置",
                    fontSize = if (compact) 30.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor
                )
            }

            item {
                SettingsSection(
                    title = "云同步",
                    compact = compact
                ) {
                    SyncCard(
                        compact = compact,
                        onSyncSettingsClick = onSyncSettingsClick
                    )
                }
            }

            item {
                SettingsSection(
                    title = "数据导入",
                    compact = compact
                ) {
                    SingleSettingCard(
                        compact = compact,
                        item = SettingsRowData(Icons.Outlined.Download, "导入本地数据", onImportClick)
                    )
                }
            }

            item {
                SettingsSection(
                    title = "导出备份",
                    compact = compact
                ) {
                    SingleSettingCard(
                        compact = compact,
                        item = SettingsRowData(Icons.Outlined.Upload, "导出备份文件", onExportClick)
                    )
                }
            }

            item {
                SettingsSection(
                    title = "应用设置",
                    compact = compact
                ) {
                    GroupedSettingCard(
                        compact = compact,
                        items = listOf(
                            SettingsRowData(Icons.Outlined.EditNote, "编辑体验", onEditorSettingsClick),
                            SettingsRowData(Icons.Outlined.NotificationsNone, "提醒设置", onReminderSettingsClick),
                            SettingsRowData(Icons.Outlined.Info, "关于 TraceDiary", onAboutClick)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    compact: Boolean,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        Text(
            text = title,
            fontSize = if (compact) 15.sp else 16.sp,
            color = SectionTitleColor
        )
        content()
    }
}

@Composable
private fun SyncCard(
    compact: Boolean,
    onSyncSettingsClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 16.dp else 18.dp,
                        vertical = if (compact) 16.dp else 18.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingBadge(
                        icon = Icons.Outlined.CloudDone,
                        compact = compact
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "云同步完成",
                            fontSize = if (compact) 18.sp else 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "最新同步：刚刚",
                            fontSize = if (compact) 13.sp else 14.sp,
                            color = SecondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                SyncIndicator(compact = compact)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = if (compact) 16.dp else 18.dp),
                color = DividerColor,
                thickness = 1.dp
            )

            SettingsRow(
                compact = compact,
                item = SettingsRowData(Icons.Outlined.Settings, "同步设置", onSyncSettingsClick),
                topPadding = if (compact) 14.dp else 16.dp,
                bottomPadding = if (compact) 16.dp else 18.dp
            )
        }
    }
}

@Composable
private fun SingleSettingCard(
    compact: Boolean,
    item: SettingsRowData
) {
    Card(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        SettingsRow(
            compact = compact,
            item = item,
            topPadding = if (compact) 15.dp else 18.dp,
            bottomPadding = if (compact) 15.dp else 18.dp
        )
    }
}

@Composable
private fun GroupedSettingCard(
    compact: Boolean,
    items: List<SettingsRowData>
) {
    Card(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                SettingsRow(
                    compact = compact,
                    item = item,
                    topPadding = if (index == 0) {
                        if (compact) 15.dp else 18.dp
                    } else {
                        if (compact) 13.dp else 16.dp
                    },
                    bottomPadding = if (index == items.lastIndex) {
                        if (compact) 15.dp else 18.dp
                    } else {
                        if (compact) 13.dp else 16.dp
                    }
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = if (compact) 16.dp else 18.dp),
                        color = DividerColor,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    compact: Boolean,
    item: SettingsRowData,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(
                start = if (compact) 16.dp else 18.dp,
                top = topPadding,
                end = if (compact) 16.dp else 18.dp,
                bottom = bottomPadding
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingBadge(
                icon = item.icon,
                compact = compact
            )
            Text(
                text = item.title,
                fontSize = if (compact) 17.sp else 18.sp,
                color = TitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        RowChevron(compact = compact)
    }
}

@Composable
private fun SettingBadge(
    icon: ImageVector,
    compact: Boolean
) {
    Box(
        modifier = Modifier
            .size(if (compact) 40.dp else 44.dp)
            .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
            .background(BadgeBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 22.dp else 24.dp),
            tint = AccentColor
        )
    }
}

@Composable
private fun SyncIndicator(compact: Boolean) {
    Box(
        modifier = Modifier
            .size(if (compact) 34.dp else 38.dp)
            .clip(CircleShape)
            .background(AccentColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun RowChevron(compact: Boolean) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
        tint = SecondaryTextColor
    )
}

private data class SettingsRowData(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit
)

private val SettingsBackground = Color(0xFFF4F6F3)
private val CardColor = Color(0xFFFCFCFA)
private val TitleColor = Color(0xFF101A16)
private val SectionTitleColor = Color(0xFF6C757A)
private val SecondaryTextColor = Color(0xFF818A8E)
private val DividerColor = Color(0xFFE3E7E4)
private val AccentColor = Color(0xFF0E7A50)
private val BadgeBackground = Color(0xFFEAF3EE)

@Preview(showBackground = true, backgroundColor = 0xFFF4F6F3, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsScreenPreview() {
    TraceDiaryTheme {
        SettingsScreen()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F6F3, widthDp = 320, heightDp = 780)
@Composable
private fun SettingsScreenCompactPreview() {
    TraceDiaryTheme {
        SettingsScreen()
    }
}
