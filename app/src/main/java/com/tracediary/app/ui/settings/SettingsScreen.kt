package com.tracediary.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.sync.SyncEngine
import com.tracediary.app.domain.usecase.ExportFormat
import com.tracediary.app.domain.usecase.ImportOptions
import com.tracediary.app.domain.usecase.MergeStrategy

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
                    "导入数据", "导出备份" -> DataManagementPage(
                        mode = page,
                        state = state,
                        viewModel = viewModel,
                    )
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Cloud, 
                        title = "云同步", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("同步设置") }
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Lock, 
                        title = "数据加密", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("数据加密") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Download, 
                        title = "导入数据", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("导入数据") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Upload, 
                        title = "导出备份", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("导出备份") }
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.NotificationsNone, 
                        title = "通知提醒", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("通知提醒") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Info, 
                        title = "关于 TraceDiary", 
                        iconBgColor = Color(0xFF4EAE7C),
                        onClick = { onNavigate("关于我们") }
                    )
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
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun EncryptionSettingsPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val rotationWorking = state.passwordRotationStatus is PasswordRotationStatus.Working
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "应用密码用于保护本地及云端数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            SubPageSection(title = "安全选项") {
                ToggleItem(
                    title = "在本机记住密码",
                    subtitle = "关闭后每次冷启动均需输入应用密码",
                    checked = state.savePasswordLocally,
                    onCheckedChange = viewModel::onToggleSavePasswordLocally
                )
            }
        }

        item {
            SubPageSection(title = "修改密码") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TraceTextField(
                        value = state.oldSyncPassword,
                        onValueChange = viewModel::onOldSyncPasswordChanged,
                        label = "旧密码",
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                    TraceTextField(
                        value = state.newSyncPassword,
                        onValueChange = viewModel::onNewSyncPasswordChanged,
                        label = "新密码",
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                    TraceTextField(
                        value = state.confirmNewSyncPassword,
                        onValueChange = viewModel::onConfirmNewSyncPasswordChanged,
                        label = "确认新密码",
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = viewModel::onRotateSyncPassword,
                        enabled = !rotationWorking && state.syncOpStatus !is SyncOpStatus.Working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (rotationWorking) "正在更新…" else "更新应用密码", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            PasswordRotationStatusRow(status = state.passwordRotationStatus)
        }
    }
}

@Composable
private fun SyncSettingsPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubPageSection(title = "基础设置") {
                ToggleItem(
                    title = "启用云同步",
                    subtitle = "开启后自动在多设备间同步加密数据",
                    checked = state.syncEnabled,
                    onCheckedChange = viewModel::onToggleSync
                )
            }
        }

        if (state.syncEnabled) {
            item {
                SubPageSection(title = "存储提供商") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TraceChoiceChip(
                            selected = state.syncProvider == "aliyun",
                            onClick = { viewModel.onSyncProviderChanged("aliyun") },
                            label = "阿里云 OSS"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TraceChoiceChip(
                            selected = state.syncProvider == "tencent",
                            onClick = { viewModel.onSyncProviderChanged("tencent") },
                            label = "腾讯云 COS"
                        )
                    }
                }
            }

            item {
                SubPageSection(title = "连接信息") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TraceTextField(value = state.syncDisplayName, onValueChange = viewModel::onSyncDisplayNameChanged, label = "空间名称 (如: 我的日记)")
                        TraceTextField(value = state.syncBucket, onValueChange = viewModel::onSyncBucketChanged, label = "存储桶名称 (Bucket)")
                        TraceTextField(value = state.syncRegion, onValueChange = viewModel::onSyncRegionChanged, label = "所在地域 (Region)")
                        TraceTextField(value = state.syncDirectory, onValueChange = viewModel::onSyncDirectoryChanged, label = "同步目录")
                    }
                }
            }

            item {
                SubPageSection(title = "访问凭证") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TraceTextField(value = state.syncAccessKeyId, onValueChange = viewModel::onSyncAccessKeyIdChanged, label = "AccessKey ID")
                        TraceTextField(value = state.syncAccessKeySecret, onValueChange = viewModel::onSyncAccessKeySecretChanged, label = "AccessKey Secret", isPassword = true)
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "提示：建议创建专用密钥，并仅授予指定存储桶的读写权限以保障安全。",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = viewModel::onSaveSyncConfiguration,
                            enabled = state.saveStatus != SaveStatus.Saving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                when (state.saveStatus) {
                                    SaveStatus.Saving -> "验证中…"
                                    SaveStatus.Saved -> "验证通过"
                                    else -> "保存并验证连接"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (state.saveStatus == SaveStatus.Failed && !state.saveError.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "连接失败：${state.saveError}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (state.saveStatus == SaveStatus.Saved) {
                item {
                    SubPageSection(title = "首次同步操作") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SyncChoiceItem(title = "上传本机数据", desc = "适合第一次使用云同步", icon = Icons.Outlined.CloudUpload, onClick = viewModel::onUploadLocal)
                            SyncChoiceItem(title = "从云端恢复", desc = "适合换机或重装 App", icon = Icons.Outlined.CloudDownload, onClick = viewModel::onDownloadRemote)
                            SyncChoiceItem(title = "合并两端数据", desc = "适合两端均有不同数据", icon = Icons.Outlined.MergeType, onClick = viewModel::onMergeAll)
                        }
                    }
                }
            }
        }
    }

    SyncOpDialogs(state = state, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    val (initialHour, initialMinute) = remember(state.dailyReminderTime) {
        parseHourMinute(state.dailyReminderTime)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionGranted(granted)
        if (granted) viewModel.onToggleReminder(true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubPageSection(title = "每日提醒") {
                ToggleItem(
                    title = "开启通知",
                    subtitle = "每天 ${state.dailyReminderTime} 提醒我写日记",
                    checked = state.dailyReminderEnabled,
                    onCheckedChange = { value ->
                        if (!value) {
                            viewModel.onToggleReminder(false)
                            return@ToggleItem
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.onToggleReminder(true) else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.onToggleReminder(true)
                        }
                    }
                )
            }
        }

        item {
            SubPageSection(title = "设置时间") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(enabled = state.dailyReminderEnabled) { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("提醒时间", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            state.dailyReminderTime,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.dailyReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        var selectedHour by remember { mutableStateOf(initialHour) }
        var selectedMinute by remember { mutableStateOf(initialMinute) }

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = { 
                WheelTimePicker(
                    initialHour = initialHour,
                    initialMinute = initialMinute,
                    onTimeChanged = { h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onReminderTimeChanged("%02d:%02d".format(selectedHour, selectedMinute))
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DataManagementPage(mode: String, state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isImport = mode == "导入数据"
    val isWorking = state.backupStatus is BackupStatus.Working
    var mergeStrategy by remember { mutableStateOf(MergeStrategy.KeepNewer) }
    var exportFormat by remember { mutableStateOf(ExportFormat.Json) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.onExport(uri, context.contentResolver, exportFormat)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImport(uri = uri, contentResolver = context.contentResolver, options = ImportOptions(mergeStrategy = mergeStrategy))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = if (isImport) "支持导入从 TraceDiary 导出的备份包。导入后记录将自动进入加密数据库。" else "导出全部日记与年度总结。导出的文件经过加密，只有您的应用密码可以解开。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isImport) {
            item {
                SubPageSection(title = "导出格式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraceChoiceChip(selected = exportFormat == ExportFormat.Json, onClick = { exportFormat = ExportFormat.Json }, label = "JSON", enabled = !isWorking)
                        TraceChoiceChip(selected = exportFormat == ExportFormat.TextZip, onClick = { exportFormat = ExportFormat.TextZip }, label = "TXT 压缩包", enabled = !isWorking)
                        TraceChoiceChip(selected = exportFormat == ExportFormat.MarkdownZip, onClick = { exportFormat = ExportFormat.MarkdownZip }, label = "Markdown", enabled = !isWorking)
                    }
                }
            }
        }

        if (isImport) {
            item {
                SubPageSection(title = "重复处理策略") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraceChoiceChip(selected = mergeStrategy == MergeStrategy.KeepNewer, onClick = { mergeStrategy = MergeStrategy.KeepNewer }, label = "保留较新", enabled = !isWorking)
                        TraceChoiceChip(selected = mergeStrategy == MergeStrategy.OverwriteAll, onClick = { mergeStrategy = MergeStrategy.OverwriteAll }, label = "全部覆盖", enabled = !isWorking)
                        TraceChoiceChip(selected = mergeStrategy == MergeStrategy.SkipExisting, onClick = { mergeStrategy = MergeStrategy.SkipExisting }, label = "跳过重复", enabled = !isWorking)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (isImport) {
                        importLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                    } else {
                        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                        exportLauncher.launch("tracediary_backup_${today}.zip")
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Icon(if (isImport) Icons.Outlined.FileOpen else Icons.Outlined.SaveAlt, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    when {
                        isWorking && isImport -> "正在导入…"
                        isWorking -> "正在导出…"
                        isImport -> "选择备份文件并开始"
                        else -> "生成备份文件"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            BackupStatusDialogs(state = state, viewModel = viewModel, isImport = isImport)
        }
    }
}

@Composable
private fun SubPageSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun TraceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun TraceChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun PasswordRotationStatusRow(status: PasswordRotationStatus) {
    when (status) {
        PasswordRotationStatus.Idle -> Unit
        PasswordRotationStatus.Working -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
        is PasswordRotationStatus.Success -> Text(text = status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        is PasswordRotationStatus.Failed -> Text(text = status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SyncChoiceItem(title: String, desc: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 备份/恢复的状态对话框处理逻辑。
 */
@Composable
private fun BackupStatusDialogs(state: SettingsUiState, viewModel: SettingsViewModel, isImport: Boolean) {
    when (val status = state.backupStatus) {
        BackupStatus.Idle, BackupStatus.Working -> Unit
        is BackupStatus.Success -> AlertDialog(
            onDismissRequest = viewModel::resetBackupStatus,
            title = { Text(if (isImport) "导入完成" else "导出完成") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::resetBackupStatus) { Text("好的") } }
        )
        is BackupStatus.Failed -> AlertDialog(
            onDismissRequest = viewModel::resetBackupStatus,
            title = { Text(if (isImport) "导入失败" else "导出失败") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::resetBackupStatus) { Text("我知道了") } }
        )
    }
}

// 补齐缺少的辅助函数和 Dialogs
@Composable
private fun SyncOpDialogs(state: SettingsUiState, viewModel: SettingsViewModel) {
    // 复用之前的对话框逻辑，但可以做视觉微调
    when (val status = state.syncOpStatus) {
        SyncOpStatus.Idle -> Unit
        is SyncOpStatus.Working -> {
            val engineState = viewModel.syncEngineState?.collectAsStateWithLifecycle()?.value
            AlertDialog(
                onDismissRequest = {},
                title = { Text(status.operation.workingTitle()) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val stageText = engineState?.stage?.let { stageLabel(it) } ?: "正在准备…"
                        Text(stageText)
                        if (engineState != null && engineState.total > 0) {
                            LinearProgressIndicator(
                                progress = { (engineState.progress.toFloat() / engineState.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            )
                            Text(text = "${engineState.progress} / ${engineState.total}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                        }
                    }
                },
                confirmButton = {},
            )
        }
        is SyncOpStatus.Done -> AlertDialog(
            onDismissRequest = viewModel::resetSyncOpStatus,
            title = { Text("${status.operation.title()}完成") },
            text = { Text(formatSummary(status.summary)) },
            confirmButton = { TextButton(onClick = viewModel::resetSyncOpStatus) { Text("好的") } },
        )
        is SyncOpStatus.Failed -> AlertDialog(
            onDismissRequest = viewModel::resetSyncOpStatus,
            title = { Text("${status.operation.title()}失败") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::resetSyncOpStatus) { Text("我知道了") } },
        )
    }
}

private fun SyncOperation.title(): String = when (this) {
    SyncOperation.Upload -> "上传"
    SyncOperation.Download -> "恢复"
    SyncOperation.Merge -> "合并"
}

private fun SyncOperation.workingTitle(): String = when (this) {
    SyncOperation.Upload -> "正在上传到云端"
    SyncOperation.Download -> "正在从云端恢复"
    SyncOperation.Merge -> "正在合并本机与云端"
}

private fun stageLabel(stage: SyncEngine.SyncStage): String = when (stage) {
    SyncEngine.SyncStage.Idle -> "准备中"
    SyncEngine.SyncStage.Uploading -> "上传中"
    SyncEngine.SyncStage.Downloading -> "下载中"
    SyncEngine.SyncStage.Merging -> "合并中"
    SyncEngine.SyncStage.Rekeying -> "正在更新同步密码"
    SyncEngine.SyncStage.Deleting -> "正在删除云端对象"
    SyncEngine.SyncStage.Done -> "已完成"
    SyncEngine.SyncStage.Failed -> "失败"
}

private fun formatSummary(summary: SyncEngine.SyncSummary): String = buildString {
    append("上传 ").append(summary.uploaded).append(" 项，")
    append("下载 ").append(summary.downloaded).append(" 项，")
    append("跳过 ").append(summary.skipped).append(" 项")
    if (summary.deleted > 0) append("，删除 ").append(summary.deleted).append(" 项")
    if (summary.failed > 0) append("，失败 ").append(summary.failed).append(" 项")
    if (summary.conflicts > 0) append("，冲突 ").append(summary.conflicts).append(" 项")
}

private fun parseHourMinute(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 21
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

@Composable
private fun AboutPage() {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AutoStories, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "TraceDiary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = "Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SettingsRow(icon = Icons.Outlined.Gavel, title = "隐私政策", onClick = { showPrivacyDialog = true })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(icon = Icons.Outlined.Update, title = "检查更新", onClick = { showUpdateDialog = true })
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("隐私政策") },
            text = {
                Text(
                    text = "TraceDiary 是一款单机日记应用。\n\n" +
                        "1. 你的所有日记和年度总结默认仅保存在本机的加密数据库中。\n" +
                        "2. 若你主动启用云同步，数据会通过 HTTPS 传输至你自行配置的对象存储。\n" +
                        "3. 应用不收集任何遥测信息，不接入第三方分析或广告 SDK。\n" +
                        "4. 您可以随时通过「导出备份」导出全部数据。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("我知道了") } }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新") },
            text = { Text("当前版本已是最新（v1.0）") },
            confirmButton = { TextButton(onClick = { showUpdateDialog = false }) { Text("好的") } }
        )
    }
}

@Composable
private fun ToggleItem(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = iconBgColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            count = 24,
            initialIndex = hour,
            onIndexChanged = { h -> 
                hour = h
                onTimeChanged(hour, minute)
            }
        )
        Text(":", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 16.dp))
        WheelColumn(
            count = 60,
            initialIndex = minute,
            onIndexChanged = { m -> 
                minute = m
                onTimeChanged(hour, minute)
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    count: Int,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .collect { (offset, index) ->
                val selected = if (offset > 20) index + 1 else index
                onIndexChanged(selected.coerceIn(0, count - 1))
            }
    }
    
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.width(60.dp).height(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
    ) {
        item { Spacer(modifier = Modifier.height(40.dp)) }
        items(count) { index ->
            val isSelected = remember(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                val offset = listState.firstVisibleItemScrollOffset
                val visibleIndex = listState.firstVisibleItemIndex
                val centerIndex = if (offset > 20) visibleIndex + 1 else visibleIndex
                index == centerIndex
            }
            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "%02d".format(index),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}
