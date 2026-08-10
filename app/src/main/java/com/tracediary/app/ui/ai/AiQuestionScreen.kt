package com.tracediary.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiSourceType

@Composable
fun AiQuestionScreen(
    onOpenEvidence: (AiEvidence) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiQuestionViewModel = viewModel(
        factory = AiQuestionViewModel.factory(
            LocalContext.current.applicationContext as TraceDiaryApplication,
        ),
    ),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        if (state.feature.availability != AiAvailability.Ready) {
            AiUnavailableContent(state.feature.availability)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = viewModel::clear, enabled = !state.isSending) {
                Icon(Icons.Outlined.ClearAll, contentDescription = null)
                Text("清空临时会话")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.turns.isEmpty()) {
                item {
                    Text(
                        text = "可以问：去年去了哪些城市？今年吃过哪些不错的餐馆？上次洗牙是什么时候？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
            items(state.turns) { turn ->
                Text(
                    text = turn.question,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp),
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(turn.answer.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        if (turn.answer.evidence.isEmpty()) {
                            Text("未找到可验证依据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("依据", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            turn.answer.evidence.forEach { evidence ->
                                Text(
                                    text = evidence.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .evidenceClick(evidence) { onOpenEvidence(evidence) }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (state.isSending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        Text("正在查找并核对证据……")
                        TextButton(onClick = viewModel::cancel) { Text("取消") }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.question,
                onValueChange = viewModel::onQuestionChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("询问你的日记……") },
                enabled = !state.isSending,
                maxLines = 4,
            )
            IconButton(onClick = viewModel::send, enabled = !state.isSending && state.question.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun AiUnavailableContent(availability: AiAvailability) {
    val (title, description) = when (availability) {
        AiAvailability.Disabled -> "AI 搜索未开启" to "请在设置中配置并开启 AI 搜索。"
        AiAvailability.NotConfigured -> "AI 服务未配置" to "请在设置中填写 Base URL、API Key 和模型名。"
        AiAvailability.ModelMissing -> "端侧模型未下载" to "请在设置中下载并校验语义模型。"
        is AiAvailability.Downloading -> "正在下载模型" to "下载进度 ${(availability.progress * 100).toInt()}%"
        AiAvailability.IndexRequired -> "本地索引尚未建立" to "请在设置中阅读隐私说明并确认建立完整索引。"
        is AiAvailability.Indexing -> "正在建立本地索引" to "${availability.completed} / ${availability.total}"
        is AiAvailability.Unavailable -> "AI 搜索暂不可用" to availability.message
        AiAvailability.Ready -> "" to ""
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun AiEvidence.label(): String {
    val sourceLabel = when (source.type) {
        AiSourceType.Diary -> source.key
        AiSourceType.AnnualSummary -> "${source.key} 年度总结"
        AiSourceType.Statistics -> "截至 ${source.key} 的本地统计"
        AiSourceType.Preferences -> "当前普通偏好"
    }
    return "$sourceLabel · “${quote.take(80)}${if (quote.length > 80) "…" else ""}”"
}

private fun Modifier.evidenceClick(evidence: AiEvidence, onClick: () -> Unit): Modifier =
    if (evidence.source.type == AiSourceType.Diary || evidence.source.type == AiSourceType.AnnualSummary) {
        clickable(onClick = onClick)
    } else {
        this
    }
