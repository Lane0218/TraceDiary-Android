package com.tracediary.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tracediary.app.domain.model.AiAvailability

@Composable
fun AiSettingsPage(
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
) {
    var showIndexConsent by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBroadConsent by remember { mutableStateOf(false) }
    val working = state.operation is AiSettingsOperation.Working

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开启 AI 搜索", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(aiAvailabilityLabel(state.feature.availability), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.feature.configuration.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        enabled = !working,
                    )
                }
            }
        }

        item {
            SectionLabel("云端 Agent")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "云端只能通过本机白名单工具读取你授权的日记证据，不能直接访问数据库或执行写操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = viewModel::onBaseUrlChanged,
                        label = { Text("HTTPS Base URL") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = viewModel::onModelChanged,
                        label = { Text("模型名") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = viewModel::onApiKeyChanged,
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::saveAndTest,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存并验证 Tool Calls") }
                }
            }
        }

        item {
            SectionLabel("端侧模型与加密索引")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "BAAI/bge-small-zh-v1.5 · INT8 ONNX · 下载约 ${state.feature.modelSizeBytes / 1024 / 1024} MB",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    when (state.feature.availability) {
                        AiAvailability.ModelMissing,
                        is AiAvailability.Unavailable -> Button(
                            onClick = viewModel::downloadModel,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("下载并校验模型") }
                        is AiAvailability.Downloading -> Text("下载中 ${(state.feature.availability.progress * 100).toInt()}%")
                        else -> Unit
                    }
                    if (state.feature.availability == AiAvailability.IndexRequired) {
                        Button(
                            onClick = { showIndexConsent = true },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("建立完整加密索引") }
                    }
                    if (state.feature.availability == AiAvailability.Ready) {
                        OutlinedButton(
                            onClick = viewModel::rebuildIndex,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重建索引") }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("删除索引") }
                    }
                    if (state.feature.availability is AiAvailability.Indexing) {
                        val progress = state.feature.availability
                        Text("索引中 ${progress.completed} / ${progress.total}")
                        OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("允许大范围分析", style = MaterialTheme.typography.bodyLarge)
                        Text("用于“去年去了哪些城市”等需要扫描完整日期范围的问题，可随时撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.feature.configuration.broadAnalysisAllowed,
                        onCheckedChange = { allowed ->
                            if (allowed) showBroadConsent = true else viewModel.setBroadAnalysisAllowed(false)
                        },
                        enabled = !working,
                    )
                }
            }
        }
    }

    if (showIndexConsent) {
        AlertDialog(
            onDismissRequest = { showIndexConsent = false },
            title = { Text("建立本地 AI 索引？") },
            text = {
                Text("应用会在本机读取日记与年度总结并生成加密向量索引。索引不保存完整正文、不上传云端，可随时删除；打开索引只依赖你的唯一应用密码。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showIndexConsent = false
                    viewModel.confirmAndBuildIndex()
                }) { Text("同意并开始") }
            },
            dismissButton = { TextButton(onClick = { showIndexConsent = false }) { Text("取消") } },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除本地 AI 索引？") },
            text = { Text("不会删除任何日记或年度总结；再次使用 AI 问答前需要重新建立索引。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteIndex()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
    if (showBroadConsent) {
        AlertDialog(
            onDismissRequest = { showBroadConsent = false },
            title = { Text("允许大范围正文分析？") },
            text = {
                Text("对于“所有、哪些、一共”等问题，云端 Agent 可能分批读取并上传你指定日期范围内的多篇日记正文。密码、密钥、密文和同步配置始终不会上传。授权会保留，直到你关闭此开关。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBroadConsent = false
                    viewModel.setBroadAnalysisAllowed(true)
                }) { Text("允许") }
            },
            dismissButton = { TextButton(onClick = { showBroadConsent = false }) { Text("取消") } },
        )
    }
    when (val operation = state.operation) {
        is AiSettingsOperation.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("请稍候") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(operation.message, modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::cancel) { Text("取消") } },
        )
        is AiSettingsOperation.Success -> ResultDialog(operation.message, viewModel::dismissOperation)
        is AiSettingsOperation.Failed -> ResultDialog(operation.message, viewModel::dismissOperation)
        AiSettingsOperation.Idle -> Unit
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun ResultDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

fun aiAvailabilityLabel(availability: AiAvailability): String = when (availability) {
    AiAvailability.Disabled -> "未开启"
    AiAvailability.NotConfigured -> "待配置"
    AiAvailability.ModelMissing -> "待下载模型"
    is AiAvailability.Downloading -> "模型下载中"
    AiAvailability.IndexRequired -> "待建立索引"
    is AiAvailability.Indexing -> "索引中 ${availability.completed}/${availability.total}"
    AiAvailability.Ready -> "已就绪"
    is AiAvailability.Unavailable -> availability.message
}
