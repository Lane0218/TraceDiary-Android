package com.tracediary.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsBackground),
        contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Text(
                text = "设置",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor
            )
        }

        item {
            SettingsSection(title = "云同步") {
                SyncCard(onSyncSettingsClick = onSyncSettingsClick)
            }
        }

        item {
            SettingsSection(title = "数据导入") {
                SingleSettingCard(
                    item = SettingsRowData(Icons.Outlined.Download, "导入本地数据", onImportClick)
                )
            }
        }

        item {
            SettingsSection(title = "导出备份") {
                SingleSettingCard(
                    item = SettingsRowData(Icons.Outlined.Upload, "导出备份文件", onExportClick)
                )
            }
        }

        item {
            SettingsSection(title = "应用设置") {
                GroupedSettingCard(
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = SectionTitleColor
        )
        content()
    }
}

@Composable
private fun SyncCard(onSyncSettingsClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingBadge(icon = Icons.Outlined.CloudDone)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "云同步完成",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitleColor
                        )
                        Text(
                            text = "最新同步：刚刚",
                            fontSize = 14.sp,
                            color = SecondaryTextColor
                        )
                    }
                }
                SyncIndicator()
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = DividerColor,
                thickness = 1.dp
            )

            SettingsRow(
                item = SettingsRowData(Icons.Outlined.Settings, "同步设置", onSyncSettingsClick),
                topPadding = 16.dp,
                bottomPadding = 18.dp
            )
        }
    }
}

@Composable
private fun SingleSettingCard(item: SettingsRowData) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        SettingsRow(
            item = item,
            topPadding = 18.dp,
            bottomPadding = 18.dp
        )
    }
}

@Composable
private fun GroupedSettingCard(items: List<SettingsRowData>) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                SettingsRow(
                    item = item,
                    topPadding = if (index == 0) 18.dp else 16.dp,
                    bottomPadding = if (index == items.lastIndex) 18.dp else 16.dp
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
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
    item: SettingsRowData,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(start = 18.dp, top = topPadding, end = 18.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingBadge(icon = item.icon)
            Text(
                text = item.title,
                fontSize = 19.sp,
                color = TitleColor
            )
        }
        RowChevron()
    }
}

@Composable
private fun SettingBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BadgeBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = AccentColor
        )
    }
}

@Composable
private fun SyncIndicator() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AccentColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
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
