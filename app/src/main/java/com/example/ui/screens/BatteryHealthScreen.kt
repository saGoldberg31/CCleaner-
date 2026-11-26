package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TipsAndUpdates
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.engine.BatteryDrainAnalyzer
import com.example.model.Formatters
import com.example.ui.CleanProUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StaggeredLiquidReveal
import com.example.ui.components.liquidGlassCard
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBatterySaverEnabled
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun BatteryHealthScreen(
    uiState: CleanProUiState,
    onBack: () -> Unit,
    onRefreshBattery: () -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit = {},
    onOptimizePowerDrain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val telemetryColors = LocalTelemetryColors.current
    val batterySaver = LocalBatterySaverEnabled.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshBattery()
    }

    val drainReport = remember(uiState.backgroundProcesses, telemetry.effectiveCpuTempCelsius) {
        BatteryDrainAnalyzer.analyzeDrain(
            processes = uiState.backgroundProcesses,
            cpuTempCelsius = telemetry.effectiveCpuTempCelsius
        )
    }

    val batteryAccentColor = when {
        telemetry.batteryTempCelsius >= 41.0f || telemetry.batteryPercent <= 15 -> telemetryColors.danger
        telemetry.isCharging -> telemetryColors.info
        telemetry.batteryPercent <= 30 || telemetry.batteryTempCelsius >= 37.5f -> telemetryColors.warning
        else -> telemetryColors.healthy
    }

    val tempAccentColor = when {
        telemetry.batteryTempCelsius >= 41.0f -> telemetryColors.danger
        telemetry.batteryTempCelsius >= 36.5f -> telemetryColors.warning
        else -> telemetryColors.healthy
    }

    val remainingTimeText = Formatters.formatRemainingTime(
        durationMillis = telemetry.batteryTimeRemainingMillis,
        isCharging = telemetry.isCharging,
        batteryPercent = telemetry.batteryPercent
    )

    val remainingLabelTitle = when {
        telemetry.isCharging && telemetry.batteryPercent >= 100 -> "حالة الشحن"
        telemetry.isCharging -> "الوقت المتبقي لاكتمال الشحن"
        else -> "الوقت المتبقي قبل النفاد"
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
            // Top Bar
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
                        modifier = Modifier.testTag("battery_health_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Column {
                        Text(
                            text = "مراقب صحة البطارية",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "الحرارة والشحن وتقدير الوقت المتبقي • تحديث كل ${uiState.telemetryIntervalSeconds}ث",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshBattery,
                    modifier = Modifier.testTag("refresh_battery_health_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث حالة البطارية"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("battery_health_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Battery Gauge & Charge/Discharge Time Estimation Card
                item {
                    StaggeredLiquidReveal(delayMillis = 0) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(
                                    shape = MaterialTheme.shapes.large,
                                    accentTint = batteryAccentColor,
                                    isElevated = true,
                                    shimmerActive = telemetry.isCharging
                                )
                                .testTag("battery_health_hero_card"),
                            shape = MaterialTheme.shapes.large,
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Charge Status Pill Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = batteryAccentColor.copy(alpha = 0.16f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (telemetry.isCharging) {
                                                    Icons.Default.BatteryChargingFull
                                                } else if (telemetry.batteryPercent <= 15) {
                                                    Icons.Default.BatteryAlert
                                                } else {
                                                    Icons.Default.BatteryStd
                                                },
                                                contentDescription = null,
                                                tint = batteryAccentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = telemetry.batteryChargeStatusLabel,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = batteryAccentColor,
                                                modifier = Modifier.testTag("battery_charge_status_badge")
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Power,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = telemetry.batteryPluggedSourceLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("battery_plugged_source")
                                            )
                                        }
                                    }
                                }

                                // Animated Radial Gauge
                                BatteryRadialGauge(
                                    percent = telemetry.batteryPercent,
                                    isCharging = telemetry.isCharging,
                                    accentColor = batteryAccentColor,
                                    reduceAnimation = batterySaver
                                )

                                // Time Remaining Until Full Charge / Discharge Banner
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .liquidGlassCard(
                                            shape = MaterialTheme.shapes.medium,
                                            accentTint = batteryAccentColor
                                        ),
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(batteryAccentColor.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (telemetry.isCharging) Icons.Default.Bolt else Icons.Default.Schedule,
                                                contentDescription = remainingLabelTitle,
                                                tint = batteryAccentColor
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = remainingLabelTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = remainingTimeText,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = batteryAccentColor,
                                                modifier = Modifier.testTag("battery_remaining_time_value")
                                            )
                                            Text(
                                                text = if (telemetry.isHardwareChargeEstimate) {
                                                    "قياس مباشر عبر مستشعر الشحن والتيار"
                                                } else {
                                                    "تقدير ذكي بناءً على النسبة والحرارة ونشاط الخلفية"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Battery Temperature & Hardware Health Diagnostics
                item {
                    StaggeredLiquidReveal(delayMillis = 45) {
                        Column {
                            SectionHeader(
                                title = "درجة الحرارة وصحة البطارية",
                                icon = Icons.Default.Thermostat
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassCard(
                                        shape = MaterialTheme.shapes.medium,
                                        accentTint = tempAccentColor
                                    )
                                    .testTag("battery_thermal_health_card"),
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(tempAccentColor.copy(alpha = 0.16f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Thermostat,
                                                    contentDescription = "حرارة البطارية",
                                                    tint = tempAccentColor
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "حرارة البطارية الآن",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = if (telemetry.batteryTempCelsius > 0f) {
                                                        "${String.format(Locale.US, "%.1f", telemetry.batteryTempCelsius)}°C"
                                                    } else "-- °C",
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = tempAccentColor,
                                                    modifier = Modifier.testTag("battery_temperature_value")
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = tempAccentColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = telemetry.batteryThermalStatusLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = tempAccentColor,
                                                    modifier = Modifier
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        .testTag("battery_thermal_status_label")
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "الصحة: ${telemetry.batteryHealthScore}/100 (${telemetry.batteryHealthLabel})",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("battery_health_score")
                                            )
                                        }
                                    }

                                    // Normalized Temperature Bar (15°C..50°C)
                                    val tempFraction = ((telemetry.batteryTempCelsius - 15f) / 35f).coerceIn(0.05f, 1f)
                                    val animatedTempFraction by animateFloatAsState(
                                        targetValue = tempFraction,
                                        animationSpec = tween(550, easing = FastOutSlowInEasing),
                                        label = "battery_temp_bar"
                                    )
                                    LinearProgressIndicator(
                                        progress = { animatedTempFraction },
                                        color = tempAccentColor,
                                        trackColor = telemetryColors.gaugeTrack,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50))
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BatteryMetricPill(
                                            label = "الجهد الكهربائي",
                                            value = if (telemetry.batteryVoltageMv > 0) "${telemetry.batteryVoltageMv} mV" else "—",
                                            modifier = Modifier.weight(1f)
                                        )
                                        BatteryMetricPill(
                                            label = "شدة التيار",
                                            value = telemetry.batteryCurrentMa?.let { ma ->
                                                val sign = if (telemetry.isCharging) "+" else "-"
                                                "$sign${abs(ma)} mA"
                                            } ?: "تلقائي",
                                            modifier = Modifier.weight(1f)
                                        )
                                        BatteryMetricPill(
                                            label = "السعة / النوع",
                                            value = telemetry.batteryEstimatedCapacityMah?.let {
                                                "$it mAh"
                                            } ?: telemetry.batteryTechnology,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Background Power Drain & Optimization Card
                item {
                    StaggeredLiquidReveal(delayMillis = 90) {
                        Column {
                            SectionHeader(
                                title = "تحليل استنزاف الطاقة",
                                icon = Icons.Default.ElectricBolt
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassCard(
                                        shape = MaterialTheme.shapes.medium,
                                        accentTint = if (drainReport.highDrainAppCount > 0) telemetryColors.warning else telemetryColors.healthy
                                    )
                                    .testTag("battery_drain_card"),
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = drainReport.thermalDrainSeverity,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (drainReport.highDrainAppCount > 0 || telemetry.batteryTempCelsius >= 38f) {
                                                    telemetryColors.warning
                                                } else {
                                                    telemetryColors.healthy
                                                }
                                            )
                                            Text(
                                                text = drainReport.recommendationAr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = "${drainReport.highDrainAppCount} مستنزف",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    if (drainReport.topDrainApps.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            drainReport.topDrainApps.forEach { proc ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = proc.appName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = "${Formatters.formatBytes(proc.processMemoryBytes)} • ${proc.resourceTier.labelAr}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = JetBrainsMonoFontFamily
                                                            ),
                                                            color = telemetryColors.warning
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onOptimizePowerDrain,
                                            enabled = !uiState.isBoostingMemory,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .liquidPressBounce()
                                                .testTag("battery_optimize_drain_button")
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
                                                text = "تحسين وإيقاف المستنزفات",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { onToggleBatterySaver(!uiState.isBatterySaverEnabled) },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("battery_saver_quick_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BatterySaver,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (uiState.isBatterySaverEnabled) "إيقاف التوفير" else "توفير الطاقة",
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Smart Battery Care Tips
                item {
                    StaggeredLiquidReveal(delayMillis = 135) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(shape = MaterialTheme.shapes.medium),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(telemetryColors.info.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TipsAndUpdates,
                                        contentDescription = null,
                                        tint = telemetryColors.info
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "نصيحة لإطالة عمر البطارية",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = when {
                                            telemetry.batteryTempCelsius >= 39f ->
                                                "الحرارة مرتفعة نسبياً؛ تجنب استخدام الألعاب أو الشحن السريع في الأماكن الحارة للحفاظ على كفاءة خلايا الليثيوم."
                                            telemetry.isCharging && telemetry.batteryPercent >= 90 ->
                                                "البطارية قريبة من الامتلاء (${telemetry.batteryPercent}%). فصل الشاحن عند الاكتمال يقلل الإجهاد الحراري."
                                            else ->
                                                "الحفاظ على الشحن بين 20% و 85% وتفعيل وضع توفير البطارية عند انخفاض الشحن يطيل العمر الافتراضي للبطارية."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
private fun BatteryRadialGauge(
    percent: Int,
    isCharging: Boolean,
    accentColor: Color,
    reduceAnimation: Boolean
) {
    val telemetryColors = LocalTelemetryColors.current
    val targetFraction = (percent / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "battery_gauge_sweep"
    )

    val pulseAlpha = if (isCharging && !reduceAnimation) {
        val infiniteTransition = rememberInfiniteTransition(label = "charging_halo")
        val p by infiniteTransition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.34f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "halo_alpha"
        )
        p
    } else 0.14f

    Box(
        modifier = Modifier.size(168.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val inset = strokeWidth / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Ambient inner glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = pulseAlpha), Color.Transparent)
                ),
                radius = size.minDimension * 0.45f
            )

            // Track arc
            drawArc(
                color = telemetryColors.gaugeTrack,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active level arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(accentColor.copy(alpha = 0.75f), accentColor, telemetryColors.info)
                ),
                startAngle = 135f,
                sweepAngle = 270f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("battery_percent_value")
            )
            Text(
                text = if (isCharging) "متصل بالشاحن" else "طاقة البطارية",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryMetricPill(
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
