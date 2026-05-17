package com.tracediary.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            LocalContext.current.applicationContext as TraceDiaryApplication
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    AnimatedContent(targetState = activeSubPage, label = "SettingsTransition") { page ->
        if (page == null) {
            MainSettingsList(modifier = modifier, onNavigate = { activeSubPage = it })
        } else {
            SettingsSubPageContainer(title = page, onBack = { activeSubPage = null }) {
                when (page) {
                    "同步设置" -> SyncSettingsPage(state = state, viewModel = viewModel)
                    "数据加密" -> EncryptionSettingsPage(state = state, viewModel = viewModel)
                    "导入数据", "导出备份" -> DataManagementPage(page)
                    "通知提醒" -> NotificationPage(state = state, viewModel = viewModel)
                    "关于我们" -> AboutPage()
                    else -> Text("开发中...")
                }
            }
        }
    }
}

@Composable
private fun MainSettingsList(modifier: Modifier = Modifier, onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            SettingsSection(title = "安全与云端") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(icon = Icons.Outlined.Lock, title = "数据加密", onClick = { onNavigate("数据加密") })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(icon = Icons.Outlined.CloudSync, title = "云同步", onClick = { onNavigate("同步设置") })
                    }
                }
            }
        }

        item {
            SettingsSection(title = "数据管理") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(icon = Icons.Outlined.Download, title = "导入数据", onClick = { onNavigate("导入数据") })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(icon = Icons.Outlined.Upload, title = "导出备份", onClick = { onNavigate("导出备份") })
                    }
                }
            }
        }

        item {
            SettingsSection(title = "应用") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(icon = Icons.Outlined.NotificationsNone, title = "通知提醒", onClick = { onNavigate("通知提醒") })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(icon = Icons.Outlined.Info, title = "关于 TraceDiary", onClick = { onNavigate("关于我们") })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubPageContainer(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun EncryptionSettingsPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    // TODO: 数据密码、确认密码属于敏感字段，待接入 EncryptedSharedPreferences/Keystore 后再持久化。
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "即使不使用云同步，本地数据也会经过此密码加密。请务必牢记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("数据密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }
        item {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }
        item {
            ToggleItem(
                title = "在本机安全保存密码",
                subtitle = "关闭后每次打开 App 均需输入密码",
                checked = state.savePasswordLocally,
                onCheckedChange = viewModel::onToggleSavePasswordLocally
            )
        }
        item {
            Button(
                onClick = {
                    // TODO: 接入加密存储后再持久化密码本身，目前仅给出占位反馈。
                    Toast.makeText(context, "密码功能即将上线", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { Text("保存密码设置") }
        }
    }
}

@Composable
private fun SyncSettingsPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ToggleItem(
                title = "启用云同步",
                subtitle = "开启后自动在多设备间同步加密数据",
                checked = state.syncEnabled,
                onCheckedChange = viewModel::onToggleSync
            )
        }

        if (state.syncEnabled) {
            item { Text("云空间类型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.syncProvider == "aliyun",
                        onClick = { viewModel.onSyncProviderChanged("aliyun") },
                        label = { Text("阿里云 OSS") }
                    )
                    FilterChip(
                        selected = state.syncProvider == "tencent",
                        onClick = { viewModel.onSyncProviderChanged("tencent") },
                        label = { Text("腾讯云 COS") }
                    )
                }
            }

            item { Text("连接信息", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item {
                OutlinedTextField(
                    value = state.syncDisplayName,
                    onValueChange = viewModel::onSyncDisplayNameChanged,
                    label = { Text("云空间名称 (如: 我的日记)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.syncBucket,
                    onValueChange = viewModel::onSyncBucketChanged,
                    label = { Text("存储桶名称 (Bucket)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.syncRegion,
                    onValueChange = viewModel::onSyncRegionChanged,
                    label = { Text("所在地域 (Region)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.syncDirectory,
                    onValueChange = viewModel::onSyncDirectoryChanged,
                    label = { Text("同步目录") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { Text("访问凭证", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item {
                OutlinedTextField(
                    value = state.syncAccessKeyId,
                    onValueChange = viewModel::onSyncAccessKeyIdChanged,
                    label = { Text("AccessKey ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.syncAccessKeySecret,
                    onValueChange = viewModel::onSyncAccessKeySecretChanged,
                    label = { Text("AccessKey Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "提示：建议创建专用密钥，并仅授予指定存储桶的读写权限以保障安全。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                val buttonLabel = when (state.saveStatus) {
                    SaveStatus.Saving -> "验证中…"
                    SaveStatus.Saved -> "已保存"
                    SaveStatus.Failed -> "保存并验证连接"
                    SaveStatus.Idle -> "保存并验证连接"
                }
                Button(
                    onClick = viewModel::onSaveSyncConfiguration,
                    enabled = state.saveStatus != SaveStatus.Saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(buttonLabel) }
            }

            if (state.saveStatus == SaveStatus.Saved) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("配置成功！请选择首次同步方式：", style = MaterialTheme.typography.titleSmall)
                            SyncChoiceItem("上传本机数据到云端", "适合第一次使用云同步")
                            SyncChoiceItem("从云端恢复到本机", "适合换机或重装 App")
                            SyncChoiceItem("合并本机和云端数据", "适合两端均有不同数据")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncChoiceItem(title: String, desc: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 真实导入/导出/合并由后续同步阶段接入。 */ },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showTimePicker by remember { mutableStateOf(false) }
    val (initialHour, initialMinute) = remember(state.dailyReminderTime) {
        parseHourMinute(state.dailyReminderTime)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ToggleItem(
            title = "每日提醒",
            subtitle = "每天 ${state.dailyReminderTime} 提醒我写日记",
            checked = state.dailyReminderEnabled,
            onCheckedChange = viewModel::onToggleReminder
        )

        ListItem(
            headlineContent = { Text("提醒时间") },
            trailingContent = {
                Text(
                    state.dailyReminderTime,
                    color = if (state.dailyReminderEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            leadingContent = { Icon(Icons.Outlined.AccessTime, null) },
            modifier = Modifier.clickable(enabled = state.dailyReminderEnabled) { showTimePicker = true }
        )
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    viewModel.onReminderTimeChanged(formatted)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}

private fun parseHourMinute(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 21
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

@Composable
private fun DataManagementPage(mode: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (mode == "导入数据") "支持从 JSON 或 TXT 文件导入历史记录。"
            else "导出您的日记为通用格式，永久保存。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // TODO: 真实的导入/导出由后续阶段接入。
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(if (mode == "导入数据") "选择文件" else "开始导出")
        }
    }
}

@Composable
private fun AboutPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoStories,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "TraceDiary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = "Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsRow(icon = Icons.Outlined.Gavel, title = "隐私政策", onClick = {})
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRow(icon = Icons.Outlined.Update, title = "检查更新", onClick = {})
            }
        }
    }
}

@Composable
private fun ToggleItem(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
