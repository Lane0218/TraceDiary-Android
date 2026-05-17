package com.tracediary.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    AnimatedContent(targetState = activeSubPage, label = "SettingsTransition") { page ->
        if (page == null) {
            MainSettingsList(onNavigate = { activeSubPage = it })
        } else {
            SettingsSubPageContainer(title = page, onBack = { activeSubPage = null }) {
                when (page) {
                    "同步设置" -> SyncSettingsPage()
                    "导入数据", "导出备份" -> DataManagementPage(page)
                    "外观风格" -> AppearancePage()
                    "通知提醒" -> NotificationPage()
                    "关于我们" -> AboutPage()
                    else -> Text("开发中...")
                }
            }
        }
    }
}

@Composable
private fun MainSettingsList(onNavigate: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(text = "设置", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(bottom = 8.dp)) }
        item { SettingsSection(title = "同步与安全") { SyncCard(onClick = { onNavigate("同步设置") }) } }
        item { SettingsSection(title = "数据管理") { Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column { SettingsRow(icon = Icons.Outlined.Download, title = "导入数据", onClick = { onNavigate("导入数据") }); HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); SettingsRow(icon = Icons.Outlined.Upload, title = "导出备份", onClick = { onNavigate("导出备份") }) } } } }
        item { SettingsSection(title = "偏好设置") { Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column { SettingsRow(icon = Icons.Outlined.Palette, title = "外观风格", onClick = { onNavigate("外观风格") }); HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); SettingsRow(icon = Icons.Outlined.NotificationsNone, title = "通知提醒", onClick = { onNavigate("通知提醒") }) } } } }
        item { SettingsSection(title = "其他") { Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { SettingsRow(icon = Icons.Outlined.Info, title = "关于 TraceDiary", onClick = { onNavigate("关于我们") }) } } }
    }
}

@Composable
private fun SettingsSubPageContainer(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "返回") }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun SyncSettingsPage() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToggleItem(title = "自动同步", subtitle = "在有网络时自动同步日记", checked = true)
        ToggleItem(title = "仅在 Wi-Fi 下同步", subtitle = "省流量模式", checked = false)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("立即手动同步") }
    }
}

@Composable
private fun DataManagementPage(mode: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = if (mode == "导入数据") "支持从 JSON 或 TXT 文件导入历史记录。" else "导出您的日记为通用格式，永久保存。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(if (mode == "导入数据") "选择文件" else "开始导出") }
    }
}

@Composable
private fun AppearancePage() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "字体大小", style = MaterialTheme.typography.labelLarge)
        Slider(value = 0.5f, onValueChange = {})
        ToggleItem(title = "跟随系统深色模式", subtitle = null, checked = true)
    }
}

@Composable
private fun NotificationPage() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToggleItem(title = "每日提醒", subtitle = "每天 21:00 提醒我写日记", checked = true)
        SettingsRow(icon = Icons.Outlined.AccessTime, title = "提醒时间", onClick = {})
    }
}

@Composable
private fun AboutPage() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoStories, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = "TraceDiary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(text = "Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column { SettingsRow(icon = Icons.Outlined.Gavel, title = "隐私政策", onClick = {}); HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant); SettingsRow(icon = Icons.Outlined.Update, title = "检查更新", onClick = {}) } }
    }
}

@Composable
private fun ToggleItem(title: String, subtitle: String?, checked: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Text(text = title, style = MaterialTheme.typography.bodyLarge); if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = {})
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)); content() } }

@Composable
private fun SyncCard(onClick: () -> Unit) { Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CloudDone, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) } ; Spacer(modifier = Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Text(text = "云同步已开启", style = MaterialTheme.typography.titleSmall); Text(text = "最近同步：刚刚", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) } } }

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) { Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(12.dp)); Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) } }
