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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "应用密码同时用于解锁、保护本地数据库与加密云端备份，请务必牢记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ToggleItem(
                title = "在本机记住密码",
                subtitle = "关闭后每次冷启动均需输入应用密码",
                checked = state.savePasswordLocally,
                onCheckedChange = viewModel::onToggleSavePasswordLocally
            )
        }
        item {
            Text("修改应用密码", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val rotationWorking = state.passwordRotationStatus is PasswordRotationStatus.Working
                    OutlinedTextField(
                        value = state.oldSyncPassword,
                        onValueChange = viewModel::onOldSyncPasswordChanged,
                        label = { Text("旧密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !rotationWorking,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = state.newSyncPassword,
                        onValueChange = viewModel::onNewSyncPasswordChanged,
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !rotationWorking,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = state.confirmNewSyncPassword,
                        onValueChange = viewModel::onConfirmNewSyncPasswordChanged,
                        label = { Text("确认新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !rotationWorking,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = viewModel::onRotateSyncPassword,
                        enabled = !rotationWorking && state.syncOpStatus !is SyncOpStatus.Working,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(if (rotationWorking) "更新中…" else "更新应用密码")
                    }
                    when (val status = state.passwordRotationStatus) {
                        PasswordRotationStatus.Idle -> Unit
                        PasswordRotationStatus.Working -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        is PasswordRotationStatus.Success -> Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is PasswordRotationStatus.Failed -> Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
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

            if (state.saveStatus == SaveStatus.Failed && !state.saveError.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = "连接失败：${state.saveError}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
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
                            SyncChoiceItem(
                                title = "上传本机数据到云端",
                                desc = "适合第一次使用云同步",
                                onClick = viewModel::onUploadLocal,
                            )
                            SyncChoiceItem(
                                title = "从云端恢复到本机",
                                desc = "适合换机或重装 App",
                                onClick = viewModel::onDownloadRemote,
                            )
                            SyncChoiceItem(
                                title = "合并本机和云端数据",
                                desc = "适合两端均有不同数据",
                                onClick = viewModel::onMergeAll,
                            )
                        }
                    }
                }
            }
        }
    }

    SyncOpDialogs(state = state, viewModel = viewModel)
}

/**
 * 三个一次性同步按钮共用的进度 / 结果 / 失败弹窗。
 *
 * 进度文案直接订阅 [SettingsViewModel.syncEngineState]：stage + progress/total，
 * 这样用户切换页面也不会丢失实时进度。
 */
@Composable
private fun SyncOpDialogs(state: SettingsUiState, viewModel: SettingsViewModel) {
    when (val status = state.syncOpStatus) {
        SyncOpStatus.Idle -> Unit
        is SyncOpStatus.Working -> {
            val engineState = viewModel.syncEngineState?.collectAsStateWithLifecycle()?.value
            AlertDialog(
                onDismissRequest = { /* 同步进行中禁止点击外部关闭 */ },
                title = { Text(status.operation.workingTitle()) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val stageText = engineState?.stage?.let { stageLabel(it) } ?: "正在准备…"
                        Text(stageText)
                        if (engineState != null && engineState.total > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (engineState.progress.toFloat() / engineState.total)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${engineState.progress} / ${engineState.total}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                // 同步操作不可中断（SyncEngine 没有提供取消 API），所以不暴露按钮。
                confirmButton = {},
            )
        }
        is SyncOpStatus.Done -> AlertDialog(
            onDismissRequest = viewModel::resetSyncOpStatus,
            title = { Text("${status.operation.title()}完成") },
            text = { Text(formatSummary(status.summary)) },
            confirmButton = {
                TextButton(onClick = viewModel::resetSyncOpStatus) { Text("好的") }
            },
        )
        is SyncOpStatus.Failed -> AlertDialog(
            onDismissRequest = viewModel::resetSyncOpStatus,
            title = { Text("${status.operation.title()}失败") },
            text = { Text(status.message) },
            confirmButton = {
                TextButton(onClick = viewModel::resetSyncOpStatus) { Text("我知道了") }
            },
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
    if (summary.errors.isNotEmpty()) {
        append("\n\n失败明细：")
        // 最多展示 5 条，避免对话框溢出。
        summary.errors.take(5).forEach { e ->
            append("\n• ").append(e)
        }
        if (summary.errors.size > 5) {
            append("\n…还有 ").append(summary.errors.size - 5).append(" 条未展开")
        }
    }
}

@Composable
private fun SyncChoiceItem(title: String, desc: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    val (initialHour, initialMinute) = remember(state.dailyReminderTime) {
        parseHourMinute(state.dailyReminderTime)
    }

    // 通知权限申请回调：把结果回传给 ViewModel；若获得授权再真正打开提醒。
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionGranted(granted)
        if (granted) {
            viewModel.onToggleReminder(true)
        }
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
            onCheckedChange = { value ->
                if (!value) {
                    // 关闭提醒不需要权限。
                    viewModel.onToggleReminder(false)
                    return@ToggleItem
                }
                // Android 13+ 需要运行时申请 POST_NOTIFICATIONS。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.onToggleReminder(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    viewModel.onToggleReminder(true)
                }
            }
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
private fun DataManagementPage(
    mode: String,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val isImport = mode == "导入数据"
    val isWorking = state.backupStatus is BackupStatus.Working

    // 导入合并策略仅作用于本次操作，不需要落库；默认"保留较新"。
    var mergeStrategy by remember { mutableStateOf(MergeStrategy.KeepNewer) }
    var exportFormat by remember { mutableStateOf(ExportFormat.Json) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(exportMimeType(exportFormat))
    ) { uri ->
        if (uri != null) {
            viewModel.onExport(uri, context.contentResolver, exportFormat)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onImport(
                uri = uri,
                contentResolver = context.contentResolver,
                options = ImportOptions(mergeStrategy = mergeStrategy),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isImport) "支持导入 JSON 备份或 TXT/Markdown ZIP 压缩包，可选择遇到重复时如何处理。"
            else "导出全部日记与年度总结，支持 JSON、TXT ZIP、Markdown ZIP 三种格式，便于跨平台备份。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isImport) {
            Text(
                "导出格式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = exportFormat == ExportFormat.Json,
                    onClick = { exportFormat = ExportFormat.Json },
                    label = { Text("JSON") },
                    enabled = !isWorking,
                )
                FilterChip(
                    selected = exportFormat == ExportFormat.TextZip,
                    onClick = { exportFormat = ExportFormat.TextZip },
                    label = { Text("TXT 压缩包") },
                    enabled = !isWorking,
                )
                FilterChip(
                    selected = exportFormat == ExportFormat.MarkdownZip,
                    onClick = { exportFormat = ExportFormat.MarkdownZip },
                    label = { Text("Markdown 压缩包") },
                    enabled = !isWorking,
                )
            }
        }

        if (isImport) {
            Text(
                "遇到重复日期时",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mergeStrategy == MergeStrategy.KeepNewer,
                    onClick = { mergeStrategy = MergeStrategy.KeepNewer },
                    label = { Text("保留较新") },
                    enabled = !isWorking,
                )
                FilterChip(
                    selected = mergeStrategy == MergeStrategy.OverwriteAll,
                    onClick = { mergeStrategy = MergeStrategy.OverwriteAll },
                    label = { Text("全部覆盖") },
                    enabled = !isWorking,
                )
                FilterChip(
                    selected = mergeStrategy == MergeStrategy.SkipExisting,
                    onClick = { mergeStrategy = MergeStrategy.SkipExisting },
                    label = { Text("跳过已存在") },
                    enabled = !isWorking,
                )
            }
        }

        Button(
            onClick = {
                if (isImport) {
                    importLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                } else {
                    val today = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                    val fileName = when (exportFormat) {
                        ExportFormat.Json -> "tracediary_backup_$today.json"
                        ExportFormat.TextZip -> "tracediary_backup_${today}_txt.zip"
                        ExportFormat.MarkdownZip -> "tracediary_backup_${today}_md.zip"
                    }
                    exportLauncher.launch(fileName)
                }
            },
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                when {
                    isWorking && isImport -> "正在导入…"
                    isWorking -> "正在导出…"
                    isImport -> "选择备份文件"
                    else -> "开始导出"
                }
            )
        }

        when (val status = state.backupStatus) {
            BackupStatus.Idle, BackupStatus.Working -> Unit
            is BackupStatus.Success -> AlertDialog(
                onDismissRequest = viewModel::resetBackupStatus,
                title = { Text(if (isImport) "导入完成" else "导出完成") },
                text = { Text(status.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::resetBackupStatus) { Text("好的") }
                },
            )
            is BackupStatus.Failed -> AlertDialog(
                onDismissRequest = viewModel::resetBackupStatus,
                title = { Text(if (isImport) "导入失败" else "导出失败") },
                text = { Text(status.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::resetBackupStatus) { Text("我知道了") }
                },
            )
        }
    }
}

private fun exportMimeType(format: ExportFormat): String = when (format) {
    ExportFormat.Json -> "application/json"
    ExportFormat.TextZip, ExportFormat.MarkdownZip -> "application/zip"
}

@Composable
private fun AboutPage() {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

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
                SettingsRow(icon = Icons.Outlined.Gavel, title = "隐私政策", onClick = { showPrivacyDialog = true })
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
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
                        "2. 若你主动启用云同步，数据会通过 HTTPS 传输至你自行配置的对象存储（如阿里云 OSS、腾讯云 COS），由你自己掌握访问凭证。\n" +
                        "3. 应用不收集任何遥测信息，不接入第三方分析或广告 SDK。\n" +
                        "4. 应用不会主动向除你配置的云存储以外的任何服务器发送日记内容。\n" +
                        "5. 你可以随时通过「导出备份」导出全部数据，或卸载应用以清除本机数据。\n" +
                        "6. 若你启用了云同步，停用同步后请自行前往对象存储删除已上传的备份文件。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("我知道了") }
            },
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新") },
            text = { Text("当前版本已是最新（v1.0）") },
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("好的") }
            },
        )
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
