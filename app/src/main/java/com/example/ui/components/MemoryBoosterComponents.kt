package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.BackgroundProcessInfo
import com.example.model.ForceStopQueueState
import com.example.model.Formatters
import com.example.model.MemoryBoostResult
import com.example.model.ProcessResourceTier
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun MemoryBoostResultBanner(
    result: MemoryBoostResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = telemetryColors.healthy,
                shimmerActive = true
            )
            .testTag("memory_boost_result_banner"),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(telemetryColors.healthy.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = telemetryColors.healthy
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.summaryMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val details = buildList {
                    if (result.ramFreedBytes > 0L) add("RAM محرر: ${Formatters.formatBytes(result.ramFreedBytes)}")
                    if (result.cacheFreedBytes > 0L) add("كاش محرر: ${Formatters.formatBytes(result.cacheFreedBytes)}")
                    if (result.backgroundKilledCount > 0) add("عمليات: ${result.backgroundKilledCount}")
                }.joinToString(" • ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = telemetryColors.healthy
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق نتيجة التعزيز"
                )
            }
        }
    }
}

@Composable
fun BackgroundProcessRow(
    process: BackgroundProcessInfo,
    onToggleSelection: () -> Unit,
    onForceStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val bitmap = remember(process.packageName) {
        runCatching { process.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap() }.getOrNull()
    }
    val tierColor = when (process.resourceTier) {
        ProcessResourceTier.CRITICAL -> telemetryColors.danger
        ProcessResourceTier.HIGH -> telemetryColors.warning
        ProcessResourceTier.MODERATE -> telemetryColors.info
    }

    val rowAccent = when {
        process.isStopped -> telemetryColors.healthy
        process.isSelected -> MaterialTheme.colorScheme.primary
        else -> null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidPressBounce(enabled = !process.isStopped)
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = rowAccent
            )
            .clickable(enabled = !process.isStopped, onClick = onToggleSelection)
            .testTag("bg_process_card_${process.packageName}"),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = process.isSelected && !process.isStopped,
                enabled = !process.isStopped,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bg_process_checkbox_${process.packageName}")
            )

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = process.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = process.appName,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = process.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tierColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = process.resourceTier.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("bg_process_tier_${process.packageName}")
                        )
                    }
                    Text(
                        text = Formatters.formatBytes(process.processMemoryBytes),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (process.isLivePssMeasured) {
                        "${process.importanceLabelAr} • قياس PSS مباشر"
                    } else {
                        process.importanceLabelAr
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (process.isStopped) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = telemetryColors.healthy.copy(alpha = 0.16f),
                    modifier = Modifier.testTag("bg_process_stopped_badge_${process.packageName}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "متوقف",
                            tint = telemetryColors.healthy,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "متوقف",
                            style = MaterialTheme.typography.labelSmall,
                            color = telemetryColors.healthy
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onForceStopClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("force_stop_button_${process.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إيقاف إجباري",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ForceStopQueueDialog(
    queue: ForceStopQueueState,
    onOpenCurrentAppInfo: (String) -> Unit,
    onSkipCurrent: () -> Unit,
    onDismiss: () -> Unit
) {
    val telemetryColors = LocalTelemetryColors.current
    val current = queue.currentItem
    val progress = if (queue.items.isNotEmpty()) {
        queue.processedCount.toFloat() / queue.items.size.toFloat()
    } else 1f

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("force_stop_queue_dialog"),
        title = {
            Text(
                text = if (queue.isComplete) {
                    "اكتمل الإيقاف الإجباري"
                } else {
                    "إيقاف العمليات الثقيلة (${queue.processedCount + 1} / ${queue.items.size})"
                },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "تم إيقاف ${queue.stoppedCount} تطبيق • التوفير المقدر: ${Formatters.formatBytes(queue.estimatedFreedRamBytes)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = telemetryColors.healthy
                )

                if (queue.isComplete) {
                    Text(
                        text = "تمت معالجة جميع التطبيقات المستهلكة للذاكرة في هذه القائمة.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("force_stop_queue_complete_message")
                    )
                } else if (current != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = current.appName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "الأثر المقدر على RAM: ${Formatters.formatBytes(current.estimatedMemoryBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = telemetryColors.warning
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "اضغط «فتح إعدادات التطبيق» بالأسفل ثم اختر «إيقاف إجباري» (Force stop)، وعند عودتك سيتم التحقق تلقائياً والانتقال للتطبيق التالي.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (queue.isComplete) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("force_stop_queue_close_button")
                ) {
                    Text("إغلاق")
                }
            } else if (current != null) {
                Button(
                    onClick = { onOpenCurrentAppInfo(current.packageName) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("force_stop_queue_open_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فتح إعدادات التطبيق")
                }
            }
        },
        dismissButton = {
            if (!queue.isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onSkipCurrent,
                        modifier = Modifier.testTag("force_stop_queue_skip_button")
                    ) {
                        Text("تخطي")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("إنهاء")
                    }
                }
            }
        }
    )
}
