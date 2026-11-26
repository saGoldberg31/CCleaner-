package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.FileCategory
import com.example.model.Formatters
import com.example.model.StorageChartMode
import com.example.ui.CleanProUiState
import com.example.ui.components.animatedGlowBorder
import com.example.ui.components.MiniMetricTile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StaggeredLiquidReveal
import com.example.ui.components.StorageCategoryChartCard
import com.example.ui.components.StorageRadialHero
import com.example.ui.components.liquidGlassCard
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun DashboardScreen(
    uiState: CleanProUiState,
    onRunDeepScan: () -> Unit,
    onCleanSelected: () -> Unit,
    onToggleOwnCache: (Boolean) -> Unit,
    onAddFolderClick: () -> Unit,
    onPickPhotosDuplicates: () -> Unit,
    onBoostMemory: () -> Unit,
    onOpenSystemStorage: () -> Unit,
    onOpenFilesTab: (FileCategory?) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit = {},
    onToggleLiquidGlass: (Boolean) -> Unit = {},
    onSelectChartMode: (StorageChartMode) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPhotoOptimizer: () -> Unit = {},
    onOpenDeviceDashboard: () -> Unit = {},
    onOpenBatteryHealth: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onRefreshClipboard: () -> Unit = {},
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val liquidEnabled = LocalLiquidGlassEnabled.current
    var showConfirmCleanDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshClipboard()
    }

    if (showConfirmCleanDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCleanDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    if (uiState.selectedFilesCount > 0) {
                        "سيتم تنظيف ${Formatters.formatBytes(uiState.totalCleanableBytes)} " +
                            "(${uiState.selectedFilesCount} ملف محدد" +
                            (if (uiState.includeOwnCacheInClean && uiState.ownCacheBytes > 0) " + الذاكرة المؤقتة الداخلية" else "") +
                            "). لا يمكن التراجع عن حذف الملفات."
                    } else {
                        "سيتم تنظيف الذاكرة المؤقتة الداخلية وتحسين الذاكرة (${Formatters.formatBytes(uiState.ownCacheBytes)})."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCleanDialog = false
                        onCleanSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCleanDialog = false }) {
                    Text(stringResource(R.string.confirm_delete_cancel))
                }
            }
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
                .testTag("dashboard_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Brand Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = uiState.telemetry.deviceModel.ifBlank { "محلل الجهاز والتخزين" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isBatterySaverEnabled) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = telemetryColors.warning.copy(alpha = 0.16f),
                                modifier = Modifier.testTag("battery_saver_active_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatterySaver,
                                        contentDescription = "توفير البطارية مفعّل",
                                        tint = telemetryColors.warning,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "طاقة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = telemetryColors.warning
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            modifier = Modifier
                                .liquidPressBounce()
                                .liquidGlassCard(
                                    shape = RoundedCornerShape(50),
                                    accentTint = if (liquidEnabled) telemetryColors.info else null,
                                    isElevated = liquidEnabled
                                )
                                .clickable { onToggleLiquidGlass(!uiState.preferences.liquidGlassEnabled) }
                                .minimumInteractiveComponentSize()
                                .testTag("liquid_glass_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "وضعية الزجاج السائل",
                                    tint = if (liquidEnabled) telemetryColors.info else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "زجاج سائل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (liquidEnabled) telemetryColors.info else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("dashboard_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_pro)
                            )
                        }
                    }
                }
            }

            // Result Banner
            item {
                AnimatedVisibility(
                    visible = uiState.cleanBanner != null,
                    enter = fadeIn(tween(240)) + slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                    ) { -it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 }
                ) {
                    uiState.cleanBanner?.let { banner ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(
                                    shape = MaterialTheme.shapes.medium,
                                    accentTint = telemetryColors.healthy,
                                    shimmerActive = true
                                ),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = telemetryColors.healthy
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = banner.message,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (banner.bytesFreed > 0L) {
                                        Text(
                                            text = "تم تحرير ${Formatters.formatBytes(banner.bytesFreed)} (${banner.filesRemoved} ملف)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = telemetryColors.healthy
                                        )
                                    }
                                }
                                IconButton(onClick = onDismissBanner) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "إغلاق التنبيه"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hero Gauge & Live Telemetry Row
            item {
                StaggeredLiquidReveal(delayMillis = 0) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animatedGlowBorder(
                                shape = MaterialTheme.shapes.large,
                                enabled = liquidEnabled
                            )
                            .liquidGlassCard(
                                shape = MaterialTheme.shapes.large,
                                accentTint = telemetryColors.healthy,
                                isElevated = true,
                                shimmerActive = true
                            ),
                        shape = MaterialTheme.shapes.large,
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        StorageRadialHero(
                            usageFraction = uiState.telemetry.storageUsageFraction,
                            usedBytes = uiState.telemetry.usedStorageBytes,
                            totalBytes = uiState.telemetry.totalStorageBytes,
                            freeBytes = uiState.telemetry.freeStorageBytes,
                            isScanning = uiState.scanProgress.isScanning,
                            isCleaning = uiState.cleaningProgress.isCleaning,
                            cleaningProgress = uiState.cleaningProgress.progress,
                            cleaningStatusLabel = uiState.cleaningProgress.statusLabel
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.scanProgress.isScanning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${uiState.scanProgress.currentStepLabel} (${uiState.scanProgress.filesScannedCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRunDeepScan,
                                enabled = !uiState.scanProgress.isScanning && !uiState.cleaningProgress.isCleaning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("scan_now_button"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.scanProgress.isScanning) {
                                        stringResource(R.string.scanning)
                                    } else {
                                        stringResource(R.string.scan_now)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (uiState.selectedFilesCount > 0) {
                                        showConfirmCleanDialog = true
                                    } else {
                                        onCleanSelected()
                                    }
                                },
                                enabled = !uiState.scanProgress.isScanning && !uiState.cleaningProgress.isCleaning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("clean_selected_button"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                if (uiState.cleaningProgress.isCleaning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "جاري التنظيف…",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.totalCleanableBytes > 0L) {
                                            "تنظيف ${Formatters.formatBytes(uiState.totalCleanableBytes)}"
                                        } else {
                                            "تنظيف"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            // Live RAM & Battery Metrics
            item {
                StaggeredLiquidReveal(delayMillis = 40) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiniMetricTile(
                            title = "ذاكرة RAM",
                            value = Formatters.formatPercent(uiState.telemetry.ramUsageFraction),
                            subtitle = "${Formatters.formatBytes(uiState.telemetry.availRamBytes)} متاحة",
                            icon = Icons.Default.Memory,
                            accentColor = telemetryColors.info,
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            title = "البطارية والحرارة",
                            value = "${uiState.telemetry.batteryPercent}%",
                            subtitle = "${String.format("%.1f", uiState.telemetry.batteryTempCelsius)}°C • ${uiState.telemetry.batteryChargeStatusLabel}",
                            icon = if (uiState.telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            accentColor = if (uiState.telemetry.batteryTempCelsius > 40f) telemetryColors.warning else telemetryColors.healthy,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenBatteryHealth() }
                                .testTag("dashboard_battery_metric_tile")
                        )
                    }
                }
            }

            // Storage Category Circular Analyzer Chart
            item {
                StaggeredLiquidReveal(delayMillis = 80) {
                    StorageCategoryChartCard(
                        slices = uiState.diskCategorySlices,
                        chartMode = uiState.storageChartMode,
                        hasConnectedFolders = uiState.safFolders.isNotEmpty(),
                        onSelectChartMode = onSelectChartMode,
                        onAddFolderClick = onAddFolderClick
                    )
                }
            }

            // Battery Saver Mode Toggle Card
            item {
                val saverEnabled = uiState.isBatterySaverEnabled
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (saverEnabled) telemetryColors.warning else null
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (saverEnabled) telemetryColors.warning.copy(alpha = 0.20f)
                                    else telemetryColors.healthy.copy(alpha = 0.14f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatterySaver,
                                contentDescription = "وضع توفير البطارية",
                                tint = if (saverEnabled) telemetryColors.warning else telemetryColors.healthy
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع توفير البطارية",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (saverEnabled) {
                                    "نشط • تعتيم الشاشة إلى 18% وتقليل نشاط الخلفية (تحديث كل ${uiState.telemetryIntervalSeconds} ثوانٍ)"
                                } else {
                                    "متوقف • سطوع عادي وتحديث فوري كل ${uiState.telemetryIntervalSeconds} ثانية"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = saverEnabled,
                            onCheckedChange = onToggleBatterySaver,
                            modifier = Modifier.testTag("battery_saver_toggle")
                        )
                    }
                }
            }

            // Quick Pro Tools Grid
            item {
                SectionHeader(
                    title = "أدوات التنظيف الاحترافية",
                    icon = Icons.Default.RocketLaunch
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = stringResource(R.string.add_folder),
                            subtitle = "${uiState.safFolders.size} مجلد متصل",
                            icon = Icons.Default.CreateNewFolder,
                            accent = telemetryColors.healthy,
                            onClick = onAddFolderClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_folder_tile")
                        )
                        QuickActionTile(
                            title = stringResource(R.string.pick_photos_duplicates),
                            subtitle = "فحص تطابق SHA-256",
                            icon = Icons.Default.ContentCopy,
                            accent = telemetryColors.info,
                            onClick = onPickPhotosDuplicates,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("photo_duplicates_tile")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "معزز الذاكرة RAM",
                            subtitle = if (uiState.activeHeavyProcessesCount > 0) {
                                "${uiState.activeHeavyProcessesCount} تطبيق ثقيل بالخلفية"
                            } else {
                                "فحص وإيقاف العمليات الثقيلة"
                            },
                            icon = Icons.Default.Memory,
                            accent = telemetryColors.warning,
                            onClick = onBoostMemory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("boost_memory_tile")
                        )
                        QuickActionTile(
                            title = stringResource(R.string.system_storage_manager),
                            subtitle = "إعدادات تخزين أندرويد",
                            icon = Icons.Default.SettingsApplications,
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onOpenSystemStorage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("system_storage_tile")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "محسن وضغط الصور",
                            subtitle = "كشف المهزوز والمكرر",
                            icon = Icons.Default.PhotoFilter,
                            accent = telemetryColors.info,
                            onClick = onOpenPhotoOptimizer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("photo_optimizer_tile")
                        )
                        QuickActionTile(
                            title = "لوحة حالة الجهاز",
                            subtitle = if (uiState.telemetry.effectiveCpuTempCelsius > 0f) {
                                "الحرارة ${String.format("%.1f", uiState.telemetry.effectiveCpuTempCelsius)}°C • ${uiState.storageHealth.status.labelAr}"
                            } else {
                                "المعالج والذاكرة وصحة التخزين"
                            },
                            icon = Icons.Default.DeveloperBoard,
                            accent = telemetryColors.healthy,
                            onClick = onOpenDeviceDashboard,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("device_dashboard_tile")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "مراقب صحة البطارية",
                            subtitle = "${uiState.telemetry.batteryPercent}% • ${Formatters.formatRemainingTime(uiState.telemetry.batteryTimeRemainingMillis, uiState.telemetry.isCharging, uiState.telemetry.batteryPercent)}",
                            icon = if (uiState.telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            accent = if (uiState.telemetry.batteryTempCelsius >= 40f) telemetryColors.warning else telemetryColors.healthy,
                            onClick = onOpenBatteryHealth,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("battery_health_tile")
                        )
                    }

                    // Clipboard Cleaner Privacy Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCard(
                                shape = MaterialTheme.shapes.medium,
                                accentTint = telemetryColors.info
                            )
                            .testTag("clipboard_cleaner_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(telemetryColors.info.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPasteOff,
                                    contentDescription = "تنظيف الحافظة",
                                    tint = telemetryColors.info
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تنظيف الحافظة (الخصوصية)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = uiState.clipboardStatus.previewSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledTonalButton(
                                onClick = onClearClipboard,
                                enabled = uiState.clipboardStatus.hasContent,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_clipboard_button")
                            ) {
                                Text("مسح الحافظة")
                            }
                        }
                    }
                }
            }

            // Clean Breakdown Card
            item {
                SectionHeader(
                    title = "تفاصيل العناصر القابلة للتنظيف",
                    icon = Icons.Default.Storage
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(shape = MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Own App Cache row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleOwnCache(!uiState.includeOwnCacheInClean) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.includeOwnCacheInClean,
                                onCheckedChange = onToggleOwnCache,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "الذاكرة المؤقتة الداخلية للتطبيق",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "ملفات الكاش المؤقتة الآمنة للحذف",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = Formatters.formatBytes(uiState.ownCacheBytes),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Category breakdown rows if folders scanned
                        FileCategory.entries.forEach { category ->
                            val items = uiState.scannedFiles.filter { it.category == category }
                            val totalBytes = items.sumOf { it.sizeBytes }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onOpenFilesTab(category) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.labelAr,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "${items.size} عنصر مكتشف",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Formatters.formatBytes(totalBytes),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = if (totalBytes > 0) telemetryColors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Onboarding Hero Banner if no SAF folders added yet
            if (uiState.safFolders.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCard(
                                shape = MaterialTheme.shapes.large,
                                accentTint = MaterialTheme.colorScheme.primary
                            ),
                        shape = MaterialTheme.shapes.large,
                        color = Color.Transparent
                    ) {
                        Column {
                            Image(
                                painter = painterResource(id = R.drawable.img_clean_shield),
                                contentDescription = "درع التنظيف العميق",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "افتح الفحص العميق للمجلدات بأمان",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "للحفاظ على خصوصيتك وتوافق أندرويد، اختر مجلد التنزيلات (Downloads) أو المستندات ليقوم التطبيق بكشف الملفات المكررة والمؤقتة وحزم APK والملفات الكبيرة.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = onAddFolderClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("اختيار مجلد للفحص الآن")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .liquidPressBounce()
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = accent
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
