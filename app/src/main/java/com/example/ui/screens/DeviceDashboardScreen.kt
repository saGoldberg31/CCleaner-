package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.model.Formatters
import com.example.model.StorageHealthStatus
import com.example.ui.CleanProUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StaggeredLiquidReveal
import com.example.ui.components.liquidGlassCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

@Composable
fun DeviceDashboardScreen(
    uiState: CleanProUiState,
    onBack: () -> Unit,
    onRunStorageDiagnostics: () -> Unit,
    onOpenMemoryBooster: () -> Unit = {},
    onOpenBatteryHealth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val health = uiState.storageHealth
    val telemetryColors = LocalTelemetryColors.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRunStorageDiagnostics()
    }

    val thermalColor = when (telemetry.thermalSeverityLevel) {
        0 -> telemetryColors.healthy
        1, 2 -> telemetryColors.warning
        else -> telemetryColors.danger
    }

    val storageHealthColor = when (health.status) {
        StorageHealthStatus.HEALTHY -> telemetryColors.healthy
        StorageHealthStatus.ATTENTION -> telemetryColors.warning
        StorageHealthStatus.CRITICAL -> telemetryColors.danger
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("device_dashboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Column {
                        Text(
                            text = "لوحة حالة الجهاز",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "مراقبة العتاد الفورية • تحديث كل ${uiState.telemetryIntervalSeconds} ثوانٍ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onRunStorageDiagnostics,
                    enabled = !uiState.isRunningStorageDiagnostics,
                    modifier = Modifier.testTag("refresh_device_dashboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث فحص العتاد"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("device_dashboard_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. CPU & Thermal Status Hero Card
                item {
                    StaggeredLiquidReveal(delayMillis = 0) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(
                                    shape = MaterialTheme.shapes.large,
                                    accentTint = thermalColor,
                                    isElevated = true,
                                    shimmerActive = true
                                )
                                .testTag("cpu_thermal_card"),
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(thermalColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Thermostat,
                                            contentDescription = "حرارة المعالج",
                                            tint = thermalColor
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "حرارة المعالج والحالة الحرارية",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = telemetry.cpuTempSourceLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("cpu_temperature_source")
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = thermalColor.copy(alpha = 0.16f),
                                    modifier = Modifier.testTag("thermal_status_badge")
                                ) {
                                    Text(
                                        text = telemetry.thermalStatusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = thermalColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (telemetry.effectiveCpuTempCelsius > 0f) {
                                        "${String.format(Locale.US, "%.1f", telemetry.effectiveCpuTempCelsius)}°C"
                                    } else {
                                        "-- °C"
                                    },
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = thermalColor,
                                    modifier = Modifier.testTag("cpu_temperature_value")
                                )
                                telemetry.thermalHeadroom?.let { headroom ->
                                    Text(
                                        text = "الهامش الحراري: ${String.format(Locale.US, "%.0f%%", (headroom * 100f).coerceIn(0f, 100f))}",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DiagnosticMetricPill(
                                    label = "أنوية المعالج",
                                    value = "${telemetry.cpuCores} نواة",
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "المعمارية",
                                    value = telemetry.cpuAbi.ifBlank { "ARM" },
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "التردد",
                                    value = when {
                                        telemetry.cpuCurrentFreqMhz != null -> "${telemetry.cpuCurrentFreqMhz} MHz"
                                        telemetry.cpuMaxFreqMhz != null -> "أقصى ${telemetry.cpuMaxFreqMhz} MHz"
                                        else -> "تلقائي"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    }
                }

                // 2. Total RAM & Memory Pressure Diagnostics
                item {
                    StaggeredLiquidReveal(delayMillis = 45) {
                        Column {
                            SectionHeader(
                                title = "تشخيص الذاكرة العشوائية (RAM)",
                                icon = Icons.Default.Memory
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassCard(
                                        shape = MaterialTheme.shapes.medium,
                                        accentTint = if (telemetry.isLowMemory) telemetryColors.danger else telemetryColors.info
                                    )
                                    .testTag("ram_health_card"),
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Transparent
                            ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "إجمالي RAM",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = Formatters.formatBytes(telemetry.totalRamBytes),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = telemetryColors.info,
                                        modifier = Modifier.testTag("total_ram_value")
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (telemetry.isLowMemory) {
                                        telemetryColors.danger.copy(alpha = 0.16f)
                                    } else {
                                        telemetryColors.healthy.copy(alpha = 0.16f)
                                    }
                                ) {
                                    Text(
                                        text = if (telemetry.isLowMemory) "ضغط ذاكرة مرتفع" else "الذاكرة مستقرة (${Formatters.formatPercent(telemetry.ramUsageFraction)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (telemetry.isLowMemory) telemetryColors.danger else telemetryColors.healthy,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            val animatedRamFraction by animateFloatAsState(
                                targetValue = telemetry.ramUsageFraction.coerceIn(0f, 1f),
                                animationSpec = tween(550, easing = FastOutSlowInEasing),
                                label = "device_ram_bar"
                            )
                            LinearProgressIndicator(
                                progress = { animatedRamFraction },
                                color = if (telemetry.isLowMemory) telemetryColors.danger else telemetryColors.info,
                                trackColor = telemetryColors.gaugeTrack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiagnosticMetricPill(
                                    label = "المستخدم",
                                    value = Formatters.formatBytes(telemetry.usedRamBytes),
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "المتاح",
                                    value = Formatters.formatBytes(telemetry.availRamBytes),
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "حد الخطر",
                                    value = Formatters.formatBytes(telemetry.ramLowThresholdBytes),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenMemoryBooster,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("device_dashboard_open_booster_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("معزز الذاكرة (${uiState.activeHeavyProcessesCount} تطبيق ثقيل)")
                            }
                        }
                    }
                        }
                    }
                }

                // 3. Storage Health Diagnostics Card
                item {
                    StaggeredLiquidReveal(delayMillis = 90) {
                        Column {
                            SectionHeader(
                                title = "تشخيص صحة التخزين والسرعة",
                                icon = Icons.Default.HealthAndSafety
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassCard(
                                        shape = MaterialTheme.shapes.large,
                                        accentTint = storageHealthColor,
                                        shimmerActive = true
                                    )
                                    .testTag("storage_health_card"),
                                shape = MaterialTheme.shapes.large,
                                color = Color.Transparent
                            ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(storageHealthColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = "صحة التخزين",
                                            tint = storageHealthColor
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = health.status.labelAr,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = storageHealthColor,
                                            modifier = Modifier.testTag("storage_health_status")
                                        )
                                        Text(
                                            text = "المساحة الحرة: ${String.format(Locale.US, "%.1f%%", health.freeSpacePercent)} (${Formatters.formatBytes(telemetry.freeStorageBytes)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = storageHealthColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${health.healthScore}/100",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = storageHealthColor,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("storage_health_score")
                                    )
                                }
                            }

                            val animatedStorageFraction by animateFloatAsState(
                                targetValue = telemetry.storageUsageFraction.coerceIn(0f, 1f),
                                animationSpec = tween(550, easing = FastOutSlowInEasing),
                                label = "device_storage_bar"
                            )
                            LinearProgressIndicator(
                                progress = { animatedStorageFraction },
                                color = storageHealthColor,
                                trackColor = telemetryColors.gaugeTrack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiagnosticMetricPill(
                                    label = "سرعة الكتابة",
                                    value = health.writeSpeedMbPerSec?.let {
                                        String.format(Locale.US, "%.0f MB/s", it)
                                    } ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "سرعة القراءة",
                                    value = health.readSpeedMbPerSec?.let {
                                        String.format(Locale.US, "%.0f MB/s", it)
                                    } ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "زمن الاستجابة",
                                    value = health.ioLatencyMs?.let { "${it} ms" } ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DiagnosticMetricPill(
                                    label = "إجمالي التخزين",
                                    value = Formatters.formatBytes(telemetry.totalStorageBytes),
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "حجم الكتلة",
                                    value = Formatters.formatBytes(health.blockSizeBytes),
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticMetricPill(
                                    label = "إجهاد الفلاش",
                                    value = if (health.isFragmentationRiskHigh) "مرتفع" else "منخفض",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = health.recommendationAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("storage_health_recommendation")
                            )

                            Button(
                                onClick = onRunStorageDiagnostics,
                                enabled = !uiState.isRunningStorageDiagnostics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("run_storage_diagnostics_button")
                            ) {
                                if (uiState.isRunningStorageDiagnostics) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.isRunningStorageDiagnostics) "جاري فحص التخزين…" else "تشغيل فحص صحة التخزين")
                            }
                        }
                    }
                        }
                    }
                }

                // 4. Battery & System Identity Summary
                item {
                    SectionHeader(
                        title = "هوية الجهاز والبطارية",
                        icon = Icons.Default.DeveloperBoard
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                            HardwareKeyValueRow(
                                label = "الجهاز",
                                value = telemetry.deviceModel.ifBlank { "Android Device" },
                                icon = Icons.Default.PhoneAndroid
                            )
                            HardwareKeyValueRow(
                                label = "النظام",
                                value = telemetry.androidVersion.ifBlank { "Android" },
                                icon = Icons.Default.DeveloperBoard
                            )
                            HardwareKeyValueRow(
                                label = "مدة التشغيل",
                                value = Formatters.formatDuration(telemetry.uptimeMillis),
                                icon = Icons.Default.Speed
                            )
                            HardwareKeyValueRow(
                                label = "البطارية",
                                value = "${telemetry.batteryPercent}% • ${String.format(Locale.US, "%.1f", telemetry.batteryTempCelsius)}°C • ${telemetry.batteryHealthScore}/100 (${telemetry.batteryHealthLabel})",
                                icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd
                            )
                            HardwareKeyValueRow(
                                label = "حالة الشحن",
                                value = "${telemetry.batteryChargeStatusLabel} (${telemetry.batteryPluggedSourceLabel})",
                                icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd
                            )
                            HardwareKeyValueRow(
                                label = if (telemetry.isCharging) "الوقت لاكتمال الشحن" else "الوقت المتبقي",
                                value = Formatters.formatRemainingTime(
                                    telemetry.batteryTimeRemainingMillis,
                                    telemetry.isCharging,
                                    telemetry.batteryPercent
                                ),
                                icon = Icons.Default.Speed
                            )
                            OutlinedButton(
                                onClick = onOpenBatteryHealth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("device_dashboard_open_battery_health_button")
                            ) {
                                Icon(
                                    imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("فتح مراقب صحة البطارية المفصل")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HardwareKeyValueRow(
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
