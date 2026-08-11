package com.tracediary.app.ui.ai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.AiAgentProgress
import com.tracediary.app.domain.model.AiAvailability
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiSourceType
import com.tracediary.app.ui.components.ReviewEmptyState
import com.tracediary.app.ui.components.ReviewEmptyStateHeaderHeight
import com.tracediary.app.ui.components.TraceConfirmDialog
import com.tracediary.app.ui.theme.BrandGreenSoft
import com.tracediary.app.ui.theme.DiaryContentTextStyle
import com.tracediary.app.ui.theme.SurfaceDivider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EvidenceDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val AiBubbleShape = RoundedCornerShape(12.dp)
private val AiBubbleMaxWidth = 320.dp
private val AiBubbleHorizontalPadding = 14.dp
private val AiBubbleVerticalPadding = 11.dp
private val ComposerButtonSize = 32.dp
private val ComposerIconSize = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    val listState = rememberLazyListState()
    var showNewSessionConfirmation by rememberSaveable { mutableStateOf(false) }
    var selectedEvidence by remember { mutableStateOf<List<AiEvidence>?>(null) }
    val latestDraftLength = state.turns.lastOrNull()?.draftText?.length ?: 0

    LaunchedEffect(state.turns.size, state.isSending, latestDraftLength) {
        val targetIndex = when {
            state.turns.isNotEmpty() -> state.turns.lastIndex
            else -> -1
        }
        if (targetIndex >= 0) listState.animateScrollToItem(targetIndex)
    }
    LaunchedEffect(state.turns.isEmpty()) {
        if (state.turns.isEmpty()) selectedEvidence = null
    }

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

        if (state.turns.isEmpty() && !state.isSending) {
            Column(modifier = Modifier.weight(1f)) {
                Spacer(Modifier.height(ReviewEmptyStateHeaderHeight))
                ReviewEmptyState(
                    title = "问问你的记录",
                    description = "从日记中找到你想回顾的答案",
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(
                    items = state.turns,
                    key = { index, turn -> "$index:${turn.question}" },
                ) { _, turn ->
                    AiConversationTurnContent(
                        turn = turn,
                        onShowEvidence = { selectedEvidence = it },
                    )
                }
            }
        }

        AiQuestionComposer(
            question = state.question,
            isSending = state.isSending,
            canStartNewSession = state.question.isNotBlank() || state.turns.isNotEmpty() || state.isSending,
            onQuestionChanged = viewModel::onQuestionChanged,
            onSend = viewModel::send,
            onCancel = viewModel::cancel,
            onStartNewSession = { showNewSessionConfirmation = true },
        )
    }

    if (showNewSessionConfirmation) {
        TraceConfirmDialog(
            title = "开始新会话？",
            message = "当前会话不会保存，开始后将无法恢复。",
            confirmText = "开始",
            onDismissRequest = { showNewSessionConfirmation = false },
            onConfirm = {
                showNewSessionConfirmation = false
                viewModel.startNewSession()
            },
        )
    }

    selectedEvidence?.let { evidence ->
        AiEvidenceSheet(
            evidence = evidence,
            onDismiss = { selectedEvidence = null },
            onOpenEvidence = { item ->
                selectedEvidence = null
                onOpenEvidence(item)
            },
        )
    }
}

@Composable
private fun AiConversationTurnContent(
    turn: AiConversationTurn,
    onShowEvidence: (List<AiEvidence>) -> Unit,
) {
    val evidence = remember(turn.answer?.evidence) {
        turn.answer?.evidence.orEmpty().distinctBy(AiEvidence::source)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = AiBubbleMaxWidth),
                shape = AiBubbleShape,
                color = BrandGreenSoft,
                border = BorderStroke(1.dp, SurfaceDivider),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = turn.question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = AiBubbleHorizontalPadding,
                        vertical = AiBubbleVerticalPadding,
                    ),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        AiAnswerBubble {
            when (turn.state) {
                AiTurnState.Loading -> AiAgentProgressContent(
                    turn.progress ?: AiAgentProgress.Understanding,
                )
                AiTurnState.Streaming -> {
                    Text(
                        text = turn.draftText.orEmpty(),
                        style = DiaryContentTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                AiTurnState.Validating -> {
                    turn.draftText?.let { draft ->
                        Text(
                            text = draft,
                            style = DiaryContentTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    AiAgentProgressContent(
                        turn.progress ?: AiAgentProgress.Validating,
                        secondary = turn.draftText != null,
                    )
                }
                AiTurnState.Answered -> {
                    turn.answer?.let { answer ->
                        Text(
                            text = answer.text,
                            style = DiaryContentTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        AiEvidenceDisclosure(
                            evidenceCount = evidence.size,
                            onClick = { onShowEvidence(evidence) },
                        )
                    }
                }
                AiTurnState.Failed,
                AiTurnState.Cancelled -> Text(
                    text = turn.statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AiAnswerBubble(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = AiBubbleMaxWidth),
        shape = AiBubbleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SurfaceDivider),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = AiBubbleHorizontalPadding,
                vertical = AiBubbleVerticalPadding,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun AiAgentProgressContent(
    progress: AiAgentProgress,
    secondary: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "ai-answer-placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ai-answer-placeholder-alpha",
    )
    Text(
        text = progress.displayText(),
        style = if (secondary) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f + alpha * 0.35f),
        modifier = Modifier.heightIn(min = 24.dp),
    )
}

private fun AiAgentProgress.displayText(): String = when (this) {
    AiAgentProgress.Understanding -> "正在理解问题…"
    AiAgentProgress.Searching -> "正在搜索相关记录…"
    AiAgentProgress.Browsing -> "正在浏览指定时间范围…"
    is AiAgentProgress.Reading -> "正在读取 $count 条相关记录…"
    AiAgentProgress.Calculating -> "正在整理记录统计…"
    AiAgentProgress.ReadingPreferences -> "正在读取相关设置…"
    AiAgentProgress.Generating -> "正在生成回答…"
    AiAgentProgress.Validating -> "正在核对引用资料…"
}

@Composable
private fun AiEvidenceDisclosure(
    evidenceCount: Int,
    onClick: () -> Unit,
) {
    if (evidenceCount == 0) {
        Text(
            text = "未找到可核对的资料",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "参考了 $evidenceCount 条资料",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiEvidenceSheet(
    evidence: List<AiEvidence>,
    onDismiss: () -> Unit,
    onOpenEvidence: (AiEvidence) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.7f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight),
        ) {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "${evidence.size} 条资料",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                itemsIndexed(
                    items = evidence,
                    key = { _, item -> "${item.source.type}:${item.source.key}" },
                ) { index, item ->
                    AiEvidenceRow(
                        evidence = item,
                        onClick = { onOpenEvidence(item) },
                    )
                    if (index < evidence.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = SurfaceDivider,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiEvidenceRow(
    evidence: AiEvidence,
    onClick: () -> Unit,
) {
    val canOpen = evidence.source.type == AiSourceType.Diary ||
        evidence.source.type == AiSourceType.AnnualSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (canOpen) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = evidence.sourceLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = evidence.quote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (canOpen) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiQuestionComposer(
    question: String,
    isSending: Boolean,
    canStartNewSession: Boolean,
    onQuestionChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onStartNewSession: () -> Unit,
) {
    val canSend = !isSending && question.isNotBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .heightIn(min = 56.dp),
        shape = AiBubbleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SurfaceDivider),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onStartNewSession,
                enabled = canStartNewSession,
                modifier = Modifier.size(ComposerButtonSize),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = BrandGreenSoft,
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "开始新会话",
                    modifier = Modifier.size(ComposerIconSize),
                )
            }

            BasicTextField(
                value = question,
                onValueChange = onQuestionChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                enabled = !isSending,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (question.isEmpty()) {
                            Text(
                                text = "问问你的日记",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            FilledIconButton(
                onClick = if (isSending) onCancel else onSend,
                enabled = isSending || canSend,
                modifier = Modifier.size(ComposerButtonSize),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSending) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isSending) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    disabledContainerColor = BrandGreenSoft,
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
            ) {
                Icon(
                    imageVector = if (isSending) Icons.Filled.Stop else Icons.Outlined.ArrowUpward,
                    contentDescription = if (isSending) "取消" else "发送",
                    modifier = Modifier.size(ComposerIconSize),
                )
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

private fun AiEvidence.sourceLabel(): String = when (source.type) {
    AiSourceType.Diary -> runCatching {
        LocalDate.parse(source.key).format(EvidenceDateFormatter)
    }.getOrDefault(source.key)
    AiSourceType.AnnualSummary -> "${source.key} 年度总结"
    AiSourceType.Statistics -> "截至 ${source.key} 的本地统计"
    AiSourceType.Preferences -> "当前普通偏好"
}
