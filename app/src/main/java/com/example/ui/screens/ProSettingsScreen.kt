package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.R
import com.example.widget.PinWidgetHelper
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.CleaningScheduleFrequency
import com.example.ui.CleanProUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.liquidGlassCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProSettingsScreen(
    uiState: CleanProUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLargeFileThresholdMb: (Int) -> Unit,
    onSetAutoCleanSchedule: (Boolean, CleaningScheduleFrequency, Boolean) -> Unit,
    onSetBatterySaver: (Boolean) -> Unit = {},
    onSetQuickCleanNotification: (Boolean) -> Unit = {},
    onSetLiquidGlass: (Boolean) -> Unit = {},
    onSetCloudSync: (Boolean) -> Unit = {},
    onAddSafFolder: () -> Unit,
    onRemoveSafFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    val telemetryColors = LocalTelemetryColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Completion notifications are optional; cleaning schedule is already persisted
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "الإعدادات وجدولة التنظيف",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "تحكم في التنظيف التلقائي وإدارة الطاقة وتفضيلات الفحص",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }

            // Automatic Daily / Weekly Cleaning Schedule
            item {
                SectionHeader(
                    title = "جدول التنظيف التلقائي",
                    icon = Icons.Default.AutoMode
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (prefs.autoCleanScheduleEnabled) telemetryColors.healthy else null
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (prefs.autoCleanScheduleEnabled) {
                                            telemetryColors.healthy.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "جدولة التنظيف",
                                    tint = if (prefs.autoCleanScheduleEnabled) {
                                        telemetryColors.healthy
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تنظيف تلقائي بدون تدخل يدوي",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "ينظف الذاكرة المؤقتة والملفات المؤقتة في المجلدات المتصلة تلقائياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                            Switch(
                                checked = prefs.autoCleanScheduleEnabled,
                                onCheckedChange = { enable ->
                                    onSetAutoCleanSchedule(
                                        enable,
                                        prefs.autoCleanFrequency,
                                        prefs.autoCleanOnlyWhenCharging
                                    )
                                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("auto_clean_schedule_switch")
                            )
                        }

                        // Daily / Weekly Frequency Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "تكرار التنظيف التلقائي",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CleaningScheduleFrequency.entries.forEach { frequency ->
                                    val chipTag = if (frequency == CleaningScheduleFrequency.DAILY) {
                                        "schedule_daily_chip"
                                    } else {
                                        "schedule_weekly_chip"
                                    }
                                    FilterChip(
                                        selected = prefs.autoCleanFrequency == frequency,
                                        onClick = {
                                            onSetAutoCleanSchedule(
                                                prefs.autoCleanScheduleEnabled,
                                                frequency,
                                                prefs.autoCleanOnlyWhenCharging
                                            )
                                        },
                                        label = {
                                            Text("${frequency.labelAr} (${frequency.detailAr})")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(chipTag)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = prefs.autoCleanScheduleEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "التشغيل أثناء الشحن فقط",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "يحافظ على البطارية بتنفيذ الصيانة أثناء توصيل الشاحن",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )
                                    }
                                    Switch(
                                        checked = prefs.autoCleanOnlyWhenCharging,
                                        onCheckedChange = { chargingOnly ->
                                            onSetAutoCleanSchedule(
                                                prefs.autoCleanScheduleEnabled,
                                                prefs.autoCleanFrequency,
                                                chargingOnly
                                            )
                                        },
                                        modifier = Modifier.testTag("schedule_charging_only_switch")
                                    )
                                }
                            }
                        }

                        // Status Summary Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                !prefs.autoCleanScheduleEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                                prefs.batterySaverEnabled -> telemetryColors.warning.copy(alpha = 0.14f)
                                else -> telemetryColors.healthy.copy(alpha = 0.14f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = when {
                                        !prefs.autoCleanScheduleEnabled ->
                                            "الحالة: متوقف"
                                        prefs.batterySaverEnabled ->
                                            "الحالة: مؤجل مؤقتاً (وضع توفير البطارية نشط)"
                                        else ->
                                            "الحالة: نشط • تنظيف تلقائي ${prefs.autoCleanFrequency.labelAr} (${prefs.autoCleanFrequency.detailAr})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        !prefs.autoCleanScheduleEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                        prefs.batterySaverEnabled -> telemetryColors.warning
                                        else -> telemetryColors.healthy
                                    },
                                    modifier = Modifier.testTag("schedule_status_label")
                                )
                                if (prefs.lastAutoCleanTimestamp > 0L) {
                                    Text(
                                        text = "آخر تنظيف تلقائي: ${dateFormat.format(Date(prefs.lastAutoCleanTimestamp))}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Clean Notification Bar Toggle
            item {
                SectionHeader(
                    title = "إشعار التنظيف السريع",
                    icon = Icons.Default.NotificationsActive
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (prefs.quickCleanNotificationEnabled) telemetryColors.info else null
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "شريط إشعار التنظيف السريع",
                                tint = telemetryColors.info
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "شريط إشعار التنظيف السريع",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "إشعار دائم يعرض حالة الذاكرة والمساحة مع زر تنظيف الكاش بضغطة واحدة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                        Switch(
                            checked = prefs.quickCleanNotificationEnabled,
                            onCheckedChange = { enable ->
                                onSetQuickCleanNotification(enable)
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("quick_clean_notification_switch")
                        )
                    }
                }
            }

            // Home Screen Widget & One-Tap Shortcuts Card
            item {
                SectionHeader(
                    title = "ودجت واختصارات الشاشة الرئيسية",
                    icon = Icons.Default.Widgets
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = telemetryColors.healthy
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(telemetryColors.healthy.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = "ودجت الشاشة الرئيسية",
                                    tint = telemetryColors.healthy
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تسريع الذاكرة بنقرة واحدة ⚡",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "أضف ودجت أو اختصاراً إلى شاشتك الرئيسية لتحرير ذاكرة RAM فوراً دون الحاجة لفتح التطبيق.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                        }

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
                                        "يمكنك إضافة ودجت «منظف برو» مباشرة من قائمة الودجات بالشاشة الرئيسية"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("settings_pin_widget_button"),
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
                                    text = "تثبيت الودجت ⚡",
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
                                    .testTag("settings_pin_shortcut_button"),
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
                                    text = "تثبيت الاختصار 🚀",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Battery Saver Mode
            item {
                SectionHeader(
                    title = "إدارة الطاقة والبطارية",
                    icon = Icons.Default.BatterySaver
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (prefs.batterySaverEnabled) telemetryColors.warning else null
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع توفير البطارية",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.batterySaverEnabled) {
                                    "مفعّل: يتم تعتيم الشاشة وتقليل نشاط الخلفية وتحديث القياسات كل 10 ثوانٍ."
                                } else {
                                    "تعتيم الشاشة وتخفيض استهلاك الخلفية وإيقاف المهام الدورية غير الضرورية."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                        Switch(
                            checked = prefs.batterySaverEnabled,
                            onCheckedChange = onSetBatterySaver,
                            modifier = Modifier.testTag("settings_battery_saver_toggle")
                        )
                    }
                }
            }

            // Cloud Sync & Backup
            item {
                SectionHeader(
                    title = "المزامنة السحابية والنسخ الاحتياطي",
                    icon = Icons.Default.Cloud
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (prefs.cloudSyncEnabled) telemetryColors.info else null
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مزامنة السجل والإعدادات سحابياً",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.cloudSyncEnabled) {
                                    "مفعّلة • مزامنة سجلات التنظيف والتفضيلات عبر السحابة بأمان تشفير تام"
                                } else {
                                    "متوقفة • جميع البيانات مخزنة محلياً على جهازك فقط"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (prefs.cloudSyncEnabled) telemetryColors.info else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("cloud_sync_status_label")
                            )
                        }
                        Switch(
                            checked = prefs.cloudSyncEnabled,
                            onCheckedChange = onSetCloudSync,
                            modifier = Modifier.testTag("cloud_sync_switch")
                        )
                    }
                }
            }

            // Large File Threshold
            item {
                SectionHeader(
                    title = "حساسية كشف الملفات الكبيرة",
                    icon = Icons.Default.Tune
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
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الحد الأدنى لحجم الملف الكبير",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${prefs.largeFileThresholdMb} MB",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = prefs.largeFileThresholdMb.toFloat(),
                            onValueChange = { onSetLargeFileThresholdMb(it.roundToInt()) },
                            valueRange = 5f..100f,
                            steps = 18
                        )
                    }
                }
            }

            // Connected Folders (SAF)
            item {
                SectionHeader(
                    title = "المجلدات المتصلة للفحص",
                    icon = Icons.Default.Folder
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
                        if (uiState.safFolders.isEmpty()) {
                            Text(
                                text = "لم يتم توصيل أي مجلدات بعد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        } else {
                            uiState.safFolders.forEach { folder ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = folder.displayName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = if (folder.hasValidPermission) "صلاحية فحص وحذف نشطة" else "صلاحية منتهية",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onRemoveSafFolder(folder.uriString) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "إزالة المجلد ${folder.displayName}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onAddSafFolder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إضافة مجلد جديد")
                        }
                    }
                }
            }

            // Liquid Glass & Animations Mode Card
            item {
                SectionHeader(
                    title = "وضعية الزجاج السائل والحركة",
                    icon = Icons.Default.AutoAwesome
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = if (prefs.liquidGlassEnabled) telemetryColors.info else null,
                            shimmerActive = prefs.liquidGlassEnabled
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (prefs.liquidGlassEnabled) telemetryColors.info.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "وضعية الزجاج السائل",
                                tint = if (prefs.liquidGlassEnabled) telemetryColors.info else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تفعيل وضعية الزجاج السائل (Liquid Glass)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.liquidGlassEnabled) {
                                    "مفعّلة • بطاقات شفافة متدرجة ولمعان انسيابي مع خلفية ديناميكية"
                                } else {
                                    "متوقفة • مظهر Material 3 الكلاسيكي عالي التباين"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (prefs.liquidGlassEnabled) telemetryColors.info else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("liquid_glass_status_label")
                            )
                        }
                        Switch(
                            checked = prefs.liquidGlassEnabled,
                            onCheckedChange = onSetLiquidGlass,
                            modifier = Modifier.testTag("liquid_glass_switch")
                        )
                    }
                }
            }

            // Theme Selector
            item {
                SectionHeader(
                    title = "المظهر",
                    icon = Icons.Default.Palette
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.DARK to "الوضع الداكن",
                        ThemeMode.LIGHT to "الوضع الفاتح",
                        ThemeMode.SYSTEM to "تلقائي"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Privacy Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassCard(
                            shape = MaterialTheme.shapes.medium,
                            accentTint = MaterialTheme.colorScheme.primary
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "الخصوصية والأمان الكامل",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "جميع عمليات فحص التخزين وبصمات SHA-256 وتحليل التطبيقات تتم محلياً على جهازك فقط دون إرسال أي بيانات للخارج، ووفق صلاحيات أندرويد الآمنة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
