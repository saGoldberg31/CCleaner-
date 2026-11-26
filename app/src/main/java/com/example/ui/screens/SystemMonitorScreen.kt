package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.R
import com.example.widget.PinWidgetHelper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.local.CleanHistoryEntity
import com.example.model.Formatters
import com.example.ui.CleanProUiState
import com.example.ui.components.BackgroundProcessRow
import com.example.ui.components.ForceStopQueueDialog
import com.example.ui.components.MemoryBoostResultBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.liquidGlassCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SystemMonitorScreen(
    uiState: CleanProUiState,
    onBoostMemory: () -> Unit,
    onClearHistory: () -> Unit,
    onScanBackgroundProcesses: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onToggleProcessSelection: (String) -> Unit = {},
    onSelectAllProcesses: (Boolean) -> Unit = {},
    onForceStopSingleProcess: (String) -> Unit = {},
    onStartForceStopQueue: () -> Unit = {},
    onForceStopAppReturned: (String) -> Unit = {},
    onSkipForceStopApp: () -> Unit = {},
    onDismissForceStopQueue: () -> Unit = {},
    onDismissBoostResult: () -> Unit = {},
    onOpenDeviceDashboard: () -> Unit = {},
    onOpenBatteryHealth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryColors = LocalTelemetryColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    var pendingForceStopPkg by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onScanBackgroundProcesses()
    }

    val forceStopAppInfoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pkg = pendingForceStopPkg
        pendingForceStopPkg = null
        if (pkg != null) {
            onForceStopAppReturned(pkg)
        }
    }

    fun launchSystemForceStopSettings(packageName: String) {
        pendingForceStopPkg = packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { forceStopAppInfoLauncher.launch(intent) }
            .onFailure { pendingForceStopPkg = null }
    }

    if (uiState.forceStopQueue.isActive) {
        ForceStopQueueDialog(
            queue = uiState.forceStopQueue,
            onOpenCurrentAppInfo = { pkg -> launchSystemForceStopSettings(pkg) },
            onSkipCurrent = onSkipForceStopApp,
            onDismiss = onDismissForceStopQueue
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("monitor_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "معزز الذاكرة ومراقب النظام",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "تحليل استهلاك الذاكرة RAM وإيقاف التطبيقات الثقيلة في الخلفية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live RAM Monitor & Memory Booster Hero Card
            item {
                val animatedRamFraction by animateFloatAsState(
                    targetValue = uiState.telemetry.ramUsageFraction.coerceIn(0f, 1f),
                    animationSpec = tween(550, easing = FastOutSlowInEasing),
                    label = "monitor_ram_bar"
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.large,
                            accentTint = if (uiState.telemetry.isLowMemory) telemetryColors.danger else telemetryColors.info,
                            isElevated = true,
                            shimmerActive = true
                        ),
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = telemetryColors.info
                                )
                                Text(
                                    text = "ذاكرة الوصول العشوائي (RAM)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = Formatters.formatPercent(uiState.telemetry.ramUsageFraction),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (uiState.telemetry.isLowMemory) telemetryColors.danger else telemetryColors.info
                            )
                        }

                        val animatedRamFractionInner by animateFloatAsState(
                            targetValue = uiState.telemetry.ramUsageFraction.coerceIn(0f, 1f),
                            animationSpec = tween(550, easing = FastOutSlowInEasing),
                            label = "monitor_ram_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedRamFractionInner },
                            color = if (uiState.telemetry.isLowMemory) telemetryColors.danger else telemetryColors.info,
                            trackColor = telemetryColors.gaugeTrack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المستخدم: ${Formatters.formatBytes(uiState.telemetry.usedRamBytes)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "المتاح: ${Formatters.formatBytes(uiState.telemetry.availRamBytes)} / ${Formatters.formatBytes(uiState.telemetry.totalRamBytes)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = telemetryColors.healthy
                            )
                        }

                        if (uiState.activeHeavyProcessesCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = telemetryColors.warning.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "تم رصد ${uiState.activeHeavyProcessesCount} تطبيق ثقيل نشط بالخلفية (${Formatters.formatBytes(uiState.selectedProcessesMemoryBytes)} محددة للتحرير)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = telemetryColors.warning,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onBoostMemory,
                                enabled = !uiState.isBoostingMemory,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitor_boost_button")
                            ) {
                                if (uiState.isBoostingMemory) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isBoostingMemory) "جاري التحرير…" else "تعزيز الذاكرة",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (uiState.selectedProcessesCount > 0) {
                                OutlinedButton(
                                    onClick = onStartForceStopQueue,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("force_stop_selected_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.StopCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إيقاف إجباري (${uiState.selectedProcessesCount})",
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Home Screen Widget & Shortcut One-Tap Quick Actions
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                OutlinedButton(
                                    onClick = {
                                        val success = PinWidgetHelper.requestPinWidget(context)
                                        val msg = if (success) {
                                            context.getString(R.string.widget_pinned_success)
                                        } else {
                                            "يمكنك سحب ودجت «منظف برو» مباشرة من قائمة الودجات بالشاشة الرئيسية"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("pin_widget_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = telemetryColors.healthy
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Widgets,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ودجت التسريع ⚡",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val success = PinWidgetHelper.requestPinShortcut(context)
                                        val msg = if (success) {
                                            context.getString(R.string.shortcut_pinned_success)
                                        } else {
                                            "يمكنك تثبيت اختصار «تسريع فوري» بالضغط المطول على أيقونة التطبيق"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("pin_shortcut_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = telemetryColors.info
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "اختصار فوري 🚀",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

            // Memory Boost Result Banner
            item {
                AnimatedVisibility(visible = uiState.memoryBoostResult != null) {
                    uiState.memoryBoostResult?.let { result ->
                        MemoryBoostResultBanner(
                            result = result,
                            onDismiss = onDismissBoostResult
                        )
                    }
                }
            }

            // Resource-Heavy Background Processes Section
            item {
                val activeList = uiState.backgroundProcesses.filter { !it.isStopped }
                val allSelected = activeList.isNotEmpty() && activeList.all { it.isSelected }
                SectionHeader(
                    title = "العمليات الثقيلة في الخلفية (${activeList.size})",
                    icon = Icons.Default.StopCircle,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeList.isNotEmpty()) {
                                TextButton(onClick = { onSelectAllProcesses(!allSelected) }) {
                                    Text(if (allSelected) "إلغاء الكل" else "تحديد الكل")
                                }
                            }
                            IconButton(
                                onClick = onScanBackgroundProcesses,
                                modifier = Modifier.testTag("refresh_processes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث العمليات الخلفية"
                                )
                            }
                        }
                    }
                )
            }

            if (!uiState.hasUsageAccess) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = telemetryColors.warning.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                telemetryColors.warning.copy(alpha = 0.45f),
                                MaterialTheme.shapes.medium
                            )
                            .testTag("memory_usage_access_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = telemetryColors.warning
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "فعّل الوصول لإحصائيات الاستخدام",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "يتيح هذا الإذن رصد التطبيقات التي تعمل مؤخراً في الخلفية وتستهلك الذاكرة بدقة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onRequestUsageAccess,
                                modifier = Modifier.testTag("grant_usage_access_memory_button")
                            ) {
                                Text("تفعيل")
                            }
                        }
                    }
                }
            }

            if (uiState.isScanningProcesses && uiState.backgroundProcesses.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري فحص التطبيقات النشطة في الخلفية…")
                    }
                }
            } else if (uiState.backgroundProcesses.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_empty_processes_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "لا توجد تطبيقات ثقيلة نشطة حالياً في الخلفية. ذاكرة جهازك مستقرة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = uiState.backgroundProcesses,
                    key = { "bg_${it.packageName}" }
                ) { process ->
                    BackgroundProcessRow(
                        process = process,
                        onToggleSelection = { onToggleProcessSelection(process.packageName) },
                        onForceStopClick = {
                            onForceStopSingleProcess(process.packageName)
                            launchSystemForceStopSettings(process.packageName)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // Hardware & Battery Telemetry
            item {
                SectionHeader(
                    title = "معلومات العتاد والبطارية الحية",
                    icon = Icons.Default.DeveloperBoard,
                    trailing = {
                        TextButton(
                            onClick = onOpenDeviceDashboard,
                            modifier = Modifier.testTag("open_device_dashboard_button")
                        ) {
                            Text("لوحة الجهاز الكاملة")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(shape = MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryRow(
                            label = "طراز الجهاز",
                            value = uiState.telemetry.deviceModel,
                            icon = Icons.Default.PhoneAndroid
                        )
                        TelemetryRow(
                            label = "نظام التشغيل",
                            value = uiState.telemetry.androidVersion,
                            icon = Icons.Default.DeveloperBoard
                        )
                        TelemetryRow(
                            label = "أنوية المعالج",
                            value = "${uiState.telemetry.cpuCores} نواة",
                            icon = Icons.Default.Memory
                        )
                        TelemetryRow(
                            label = "البطارية والحرارة",
                            value = "${uiState.telemetry.batteryPercent}% • ${String.format(Locale.US, "%.1f", uiState.telemetry.batteryTempCelsius)}°C • ${uiState.telemetry.batteryVoltageMv}mV",
                            icon = Icons.Default.Thermostat
                        )
                        TelemetryRow(
                            label = "حالة البطارية",
                            value = "${uiState.telemetry.batteryChargeStatusLabel} • ${uiState.telemetry.batteryHealthLabel} (${uiState.telemetry.batteryHealthScore}/100)",
                            icon = Icons.Default.Thermostat
                        )
                        TelemetryRow(
                            label = if (uiState.telemetry.isCharging) "الوقت لاكتمال الشحن" else "الوقت المتبقي قبل النفاد",
                            value = Formatters.formatRemainingTime(
                                uiState.telemetry.batteryTimeRemainingMillis,
                                uiState.telemetry.isCharging,
                                uiState.telemetry.batteryPercent
                            ),
                            icon = Icons.Default.BatterySaver
                        )
                        TelemetryRow(
                            label = "نشاط الخلفية",
                            value = if (uiState.isBatterySaverEnabled) {
                                "مخفض (كل ${uiState.telemetryIntervalSeconds}ث)"
                            } else {
                                "مباشر (كل ${uiState.telemetryIntervalSeconds}ث)"
                            },
                            icon = Icons.Default.BatterySaver
                        )
                        TelemetryRow(
                            label = "سطوع النافذة",
                            value = if (uiState.isBatterySaverEnabled) "معتّم (18%)" else "تلقائي النظام",
                            icon = Icons.Default.BrightnessLow
                        )
                        OutlinedButton(
                            onClick = onOpenBatteryHealth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("open_battery_health_button")
                        ) {
                            Text("فتح مراقب صحة البطارية المفصل")
                        }
                    }
                }
            }

            // Clean History Section
            item {
                SectionHeader(
                    title = "سجل التنظيف (${Formatters.formatBytes(uiState.totalFreedEverBytes)} محررة إجمالاً)",
                    icon = Icons.Default.History,
                    trailing = {
                        if (uiState.history.isNotEmpty()) {
                            IconButton(onClick = onClearHistory) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "مسح السجل",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            if (uiState.history.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "لم يتم تسجيل عمليات تنظيف بعد. قم بإجراء فحص أو تنظيف سريع وسيتم توثيق المساحة المحررة هنا.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(uiState.history, key = { it.id }) { entry ->
                    HistoryItemCard(
                        entry = entry,
                        dateFormat = dateFormat,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HistoryItemCard(
    entry: CleanHistoryEntity,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.categoriesSummary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${dateFormat.format(Date(entry.timestampMillis))} • ${entry.filesRemoved} ملف • ${entry.durationMillis}ms",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "+${Formatters.formatBytes(entry.bytesFreed)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = telemetryColors.healthy
            )
        }
    }
}
