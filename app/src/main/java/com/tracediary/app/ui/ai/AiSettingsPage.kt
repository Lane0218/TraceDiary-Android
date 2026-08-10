package com.tracediary.app.ui.ai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.ui.components.SettingsRowDivider
import com.tracediary.app.ui.components.SettingsValueField
import com.tracediary.app.ui.components.TraceConfirmDialog
import com.tracediary.app.ui.components.TraceMessageDialog
import com.tracediary.app.ui.components.TraceProgressDialog
import com.tracediary.app.ui.components.TraceSwitch
import java.util.Locale

@Composable
fun AiSettingsPage(
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
) {
    var showIndexConsent by remember { mutableStateOf(false) }
    var showClearIndexConfirm by remember { mutableStateOf(false) }
    val working = state.operation is AiSettingsOperation.Working
    val modelSize = formatModelSize(state.feature.modelSizeBytes)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI搜索",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TraceSwitch(
                        checked = state.feature.configuration.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        enabled = !working,
                    )
                }
            }
        }

        item {
            SectionLabel("端侧模型与加密索引")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::downloadModel,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (state.feature.modelDownloaded) {
                                "重新下载模型（$modelSize）"
                            } else {
                                "下载模型（$modelSize）"
                            },
                        )
                    }
                    if (state.feature.indexReady) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::rebuildIndex,
                                enabled = !working,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("重建索引")
                            }
                            OutlinedButton(
                                onClick = { showClearIndexConfirm = true },
                                enabled = !working,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) {
                                Text("清理索引")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showIndexConsent = true },
                            enabled = !working && state.feature.modelDownloaded,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("建立索引")
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("云端大模型")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    SettingsValueField(
                        label = "Base URL",
                        value = state.baseUrl,
                        onValueChange = viewModel::onBaseUrlChanged,
                        enabled = !working,
                    )
                    SettingsRowDivider()
                    SettingsValueField(
                        label = "API Key",
                        value = state.apiKey,
                        onValueChange = viewModel::onApiKeyChanged,
                        isPassword = true,
                        enabled = !working,
                    )
                    SettingsRowDivider()
                    SettingsValueField(
                        label = "模型名",
                        value = state.model,
                        onValueChange = viewModel::onModelChanged,
                        enabled = !working,
                    )
                }
            }
        }

        item {
            Button(
                onClick = viewModel::saveAndTest,
                enabled = !working,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("保存并验证") }
        }
    }

    if (showIndexConsent) {
        TraceConfirmDialog(
            title = "建立本地 AI 索引？",
            message = "应用会在本机读取日记与年度总结并生成加密向量索引。索引不保存完整正文、不上传云端，可随时清理；打开索引只依赖你的唯一应用密码。",
            confirmText = "同意并开始",
            onDismissRequest = { showIndexConsent = false },
            onConfirm = {
                showIndexConsent = false
                viewModel.confirmAndBuildIndex()
            },
        )
    }
    if (showClearIndexConfirm) {
        TraceConfirmDialog(
            title = "清理本地索引？",
            message = "只清理可重建的加密索引，不删除日记、端侧模型或云端配置。再次使用 AI 问答前需要重新建立索引。",
            confirmText = "清理",
            onDismissRequest = { showClearIndexConfirm = false },
            onConfirm = {
                showClearIndexConfirm = false
                viewModel.deleteIndex()
            },
            destructive = true,
        )
    }
    when (val operation = state.operation) {
        is AiSettingsOperation.Working -> TraceProgressDialog(
            title = "请稍候",
            message = operation.message,
            onDismissRequest = {},
            cancelText = "取消",
            onCancel = viewModel::cancel,
        )
        is AiSettingsOperation.Success -> TraceMessageDialog(
            title = "操作完成",
            message = operation.message,
            confirmText = "知道了",
            onDismissRequest = viewModel::dismissOperation,
            onConfirm = viewModel::dismissOperation,
        )
        is AiSettingsOperation.Failed -> TraceMessageDialog(
            title = "操作失败",
            message = operation.message,
            confirmText = "知道了",
            onDismissRequest = viewModel::dismissOperation,
            onConfirm = viewModel::dismissOperation,
        )
        AiSettingsOperation.Idle -> Unit
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

private fun formatModelSize(sizeBytes: Long): String =
    String.format(Locale.ROOT, "%.1f MB", sizeBytes / 1_000_000.0)

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
