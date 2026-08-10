package com.tracediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TraceDialogSurface(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
fun TraceDialog(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    destructiveConfirm: Boolean = false,
) {
    TraceDialogSurface(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .heightIn(max = 360.dp),
            ) {
                content()
            }
            if (dismissText != null || confirmText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss ?: onDismissRequest) {
                            Text(dismissText)
                        }
                    }
                    if (confirmText != null && onConfirm != null) {
                        TextButton(
                            onClick = onConfirm,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (destructiveConfirm) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        ) {
                            Text(confirmText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TraceConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
) {
    TraceDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        dismissText = "取消",
        onDismiss = onDismissRequest,
        confirmText = confirmText,
        onConfirm = onConfirm,
        destructiveConfirm = destructive,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
fun TraceMessageDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TraceDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmText = confirmText,
        onConfirm = onConfirm,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

@Composable
fun TraceProgressDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit = {},
    progress: Float? = null,
    progressLabel: String? = null,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    TraceDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        dismissText = cancelText,
        onDismiss = onCancel,
        content = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
                if (progressLabel != null) {
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}
