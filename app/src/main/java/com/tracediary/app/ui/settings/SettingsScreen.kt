package com.tracediary.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.ui.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.settings.SameDayEntrySortOrder
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
            MainSettingsList(
                state = state,
                modifier = modifier,
                onNavigate = { activeSubPage = it }
            )
        } else {
            SettingsSubPageContainer(title = page, onBack = { activeSubPage = null }) {
                when (page) {
                    "偏好设置" -> PreferenceSettingsPage(state = state, viewModel = viewModel)
                    "同步设置" -> SyncSettingsPage(state = state, viewModel = viewModel)
                    "数据加密" -> EncryptionSettingsPage(state = state, viewModel = viewModel)
                    "备份与恢复" -> DataManagementPage(
                        state = state,
                        viewModel = viewModel,
                    )
                    "通知提醒" -> NotificationPage(state = state, viewModel = viewModel)
                    "关于" -> AboutPage()
                    else -> Text("开发中...")
                }
            }
        }
    }
}

@Composable
private fun MainSettingsList(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
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
                        subtitle = "最近同步: ${state.lastSyncText}",
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
                        onClick = { onNavigate("数据加密") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.ImportExport,
                        title = "备份与恢复",
                        onClick = { onNavigate("备份与恢复") }
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
                        onClick = { onNavigate("通知提醒") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        title = "偏好设置",
                        onClick = { onNavigate("偏好设置") }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Info, 
                        title = "关于",
                        onClick = { onNavigate("关于") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSettingsPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "往年今日记录排序",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    ChoiceRow(
                        label = "按年份从晚到早显示",
                        selected = state.sameDayEntrySortOrder == SameDayEntrySortOrder.LatestFirst,
                        enabled = true,
                        onClick = { viewModel.onSameDayEntrySortOrderChanged(SameDayEntrySortOrder.LatestFirst) }
                    )
                    SyncRowDivider()
                    ChoiceRow(
                        label = "按年份从早到晚显示",
                        selected = state.sameDayEntrySortOrder == SameDayEntrySortOrder.EarliestFirst,
                        enabled = true,
                        onClick = { viewModel.onSameDayEntrySortOrderChanged(SameDayEntrySortOrder.EarliestFirst) }
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
            .imePadding()
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
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "记住密码",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.savePasswordLocally,
                        onCheckedChange = viewModel::onToggleSavePasswordLocally,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = "修改密码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SyncFieldRow(
                        label = "旧密码",
                        value = state.oldSyncPassword,
                        onValueChange = viewModel::onOldSyncPasswordChanged,
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                    SyncRowDivider()
                    SyncFieldRow(
                        label = "新密码",
                        value = state.newSyncPassword,
                        onValueChange = viewModel::onNewSyncPasswordChanged,
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                    SyncRowDivider()
                    SyncFieldRow(
                        label = "确认新密码",
                        value = state.confirmNewSyncPassword,
                        onValueChange = viewModel::onConfirmNewSyncPasswordChanged,
                        isPassword = true,
                        enabled = !rotationWorking
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = viewModel::onRotateSyncPassword,
                enabled = !rotationWorking && state.syncOpStatus !is SyncOpStatus.Working,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (rotationWorking) "正在更新…" else "更新密码",
                    fontWeight = FontWeight.Medium
                )
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
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "云同步",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.syncEnabled,
                        onCheckedChange = viewModel::onToggleSync,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        if (state.syncEnabled) {
            item {
                SyncProviderSelector(
                    selectedProvider = state.syncProvider,
                    onProviderSelected = viewModel::onSyncProviderChanged
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        SyncFieldRow(
                            label = "存储桶",
                            value = state.syncBucket,
                            onValueChange = viewModel::onSyncBucketChanged
                        )
                        SyncRowDivider()
                        SyncFieldRow(
                            label = "所在地域",
                            value = state.syncRegion,
                            onValueChange = viewModel::onSyncRegionChanged
                        )
                        SyncRowDivider()
                        SyncFieldRow(
                            label = "同步目录",
                            value = state.syncDirectory,
                            onValueChange = viewModel::onSyncDirectoryChanged
                        )
                        SyncRowDivider()
                        SyncFieldRow(
                            label = "AccessKey ID",
                            value = state.syncAccessKeyId,
                            onValueChange = viewModel::onSyncAccessKeyIdChanged
                        )
                        SyncRowDivider()
                        SyncFieldRow(
                            label = "AccessKey Secret",
                            value = state.syncAccessKeySecret,
                            onValueChange = viewModel::onSyncAccessKeySecretChanged,
                            isPassword = true
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Button(
                    onClick = viewModel::onSaveSyncConfiguration,
                    enabled = state.saveStatus != SaveStatus.Saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        if (state.saveStatus == SaveStatus.Saving) "验证中…" else "保存并检查",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    SyncOpDialogs(state = state, viewModel = viewModel)
}

@Composable
private fun SyncRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun SyncProviderSelector(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    val providers = listOf("aliyun" to "阿里云", "tencent" to "腾讯云")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        providers.forEach { (id, label) ->
            val selected = selectedProvider == id
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onProviderSelected(id) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun SyncFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 12.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "未填写",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            }
        )
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionGranted(granted)
        if (granted) viewModel.onToggleReminder(true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "开启通知",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.dailyReminderEnabled,
                            onCheckedChange = { value ->
                                if (!value) {
                                    viewModel.onToggleReminder(false)
                                    return@Switch
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.onToggleReminder(true) else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.onToggleReminder(true)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    SyncRowDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(enabled = state.dailyReminderEnabled) { showTimePicker = true }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提醒时间",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.dailyReminderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = state.dailyReminderTime,
                            style = MaterialTheme.typography.bodyLarge,
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

        BasicAlertDialog(
            onDismissRequest = { showTimePicker = false },
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择提醒时间",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                WheelTimePicker(
                    initialHour = selectedHour,
                    initialMinute = selectedMinute,
                    onTimeChanged = { h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.onReminderTimeChanged(
                            String.format("%02d:%02d", selectedHour, selectedMinute)
                        )
                        showTimePicker = false
                    }) {
                        Text("确定", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataManagementPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf("import") }
    val isImport = selectedMode == "import"
    val isWorking = state.backupStatus is BackupStatus.Working
    var mergeStrategy by remember { mutableStateOf(MergeStrategy.KeepNewer) }
    var exportFormat by remember { mutableStateOf(ExportFormat.Json) }
    var lastIsImport by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            lastIsImport = false
            viewModel.onExport(uri, context.contentResolver, exportFormat)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lastIsImport = true
            viewModel.onImport(uri = uri, contentResolver = context.contentResolver, options = ImportOptions(mergeStrategy = mergeStrategy))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            BackupModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )
        }

        item {
            Text(
                text = if (isImport) "重复记录处理方式" else "文件格式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    if (isImport) {
                        val strategies = listOf(
                            Triple(MergeStrategy.KeepNewer, "保留较新", "同一天有多条记录时，保留更新时间较新的一条"),
                            Triple(MergeStrategy.OverwriteAll, "全部覆盖", "用导入文件中的记录覆盖本机同日的已有记录"),
                            Triple(MergeStrategy.SkipExisting, "跳过", "保留本机原有记录，忽略导入文件中的重复项")
                        )
                        strategies.forEachIndexed { index, (strategy, label, desc) ->
                            ChoiceRow(
                                label = label,
                                subtitle = desc,
                                selected = mergeStrategy == strategy,
                                enabled = !isWorking,
                                onClick = { mergeStrategy = strategy }
                            )
                            if (index < strategies.lastIndex) SyncRowDivider()
                        }
                    } else {
                        val formats = listOf(
                            ExportFormat.Json to "JSON",
                            ExportFormat.TextZip to "TXT",
                            ExportFormat.MarkdownZip to "Markdown"
                        )
                        formats.forEachIndexed { index, (format, label) ->
                            ChoiceRow(
                                label = label,
                                selected = exportFormat == format,
                                enabled = !isWorking,
                                onClick = { exportFormat = format }
                            )
                            if (index < formats.lastIndex) SyncRowDivider()
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = {
                    if (isImport) {
                        importLauncher.launch(arrayOf("*/*"))
                    } else {
                        exportLauncher.launch("TraceDiary_Backup_${java.time.LocalDate.now()}.zip")
                    }
                },
                enabled = !isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isImport) "选择备份文件" else "生成备份文件",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    BackupStatusDialogs(state = state, viewModel = viewModel, isImport = lastIsImport)
}

@Composable
private fun BackupModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf("import" to "导入", "export" to "导出")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        modes.forEach { (id, label) ->
            val selected = selectedMode == id
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onModeSelected(id) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp)
            )
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
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
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
        color = Color.Transparent
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
        BackupStatus.Idle -> Unit
        BackupStatus.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (isImport) "正在导入数据" else "正在导出数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isImport) "正在解析备份并写入本机…" else "正在生成备份文件…")
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            },
            confirmButton = {},
        )
        is BackupStatus.Success -> AlertDialog(
            onDismissRequest = viewModel::resetBackupStatus,
            title = { Text(if (isImport) "导入完成" else "导出完成") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::resetBackupStatus) { Text("好的") } },
        )
        is BackupStatus.Failed -> AlertDialog(
            onDismissRequest = viewModel::resetBackupStatus,
            title = { Text(if (isImport) "导入失败" else "导出失败") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::resetBackupStatus) { Text("我知道了") } },
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
        // Logo Section
        Icon(
            imageVector = Icons.Outlined.AutoStories,
            contentDescription = "Logo",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "TraceDiary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "版本：1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                AboutRow(title = "隐私政策", onClick = { showPrivacyDialog = true })
                HorizontalDivider(modifier = Modifier.padding(start = 20.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                AboutRow(title = "检查更新", onClick = { showUpdateDialog = true })
            }
        }
    }

    if (showPrivacyDialog) {
        TraceInfoDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("隐私政策") },
            content = {
                Text(
                    text = "TraceDiary 是一款单机日记应用。\n\n" +
                        "1. 你的所有日记和年度总结默认仅保存在本机的加密数据库中。\n" +
                        "2. 若你主动启用云同步，数据会通过 HTTPS 传输至你自行配置的对象存储。\n" +
                        "3. 应用不收集任何遥测信息，不接入第三方分析或广告 SDK。\n" +
                        "4. 您可以随时通过「导出备份」导出全部数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )
            },
            confirmText = "我知道了",
            onConfirm = { showPrivacyDialog = false }
        )
    }

    if (showUpdateDialog) {
        TraceInfoDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新") },
            content = {
                Text(
                    text = "当前版本已是最新（v1.0）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmText = "好的",
            onConfirm = { showUpdateDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraceInfoDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            ) {
                title()
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = confirmText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
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
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
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
