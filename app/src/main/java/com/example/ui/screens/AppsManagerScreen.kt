package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.model.AppCacheClearQueueState
import com.example.model.AppCacheItemStatus
import com.example.model.AppImpactLevel
import com.example.model.AppSortFilter
import com.example.model.AppUninstallQueueState
import com.example.model.AppUninstallStatus
import com.example.model.Formatters
import com.example.model.InstalledAppInfo
import com.example.ui.CleanProUiState
import com.example.ui.components.liquidGlassCard
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppsManagerScreen(
    uiState: CleanProUiState,
    onRefreshApps: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onSelectSortFilter: (AppSortFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onToggleAppSelection: (String) -> Unit = {},
    onSelectAllApps: (Boolean) -> Unit = {},
    onClearSelectedCaches: () -> Unit = {},
    onClearSingleAppCache: (String) -> Unit = {},
    onAppSettingsReturned: (String) -> Unit = {},
    onSkipQueueApp: () -> Unit = {},
    onDismissQueue: () -> Unit = {},
    onUninstallSelectedApps: () -> Unit = {},
    onAppUninstallReturned: (String) -> Unit = {},
    onSkipUninstallApp: () -> Unit = {},
    onDismissUninstallQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryColors = LocalTelemetryColors.current
    var pendingQueuePackage by remember { mutableStateOf<String?>(null) }
    var pendingUninstallPackage by remember { mutableStateOf<String?>(null) }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pkg = pendingQueuePackage
        pendingQueuePackage = null
        if (pkg != null) {
            onAppSettingsReturned(pkg)
        }
    }

    val appUninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pkg = pendingUninstallPackage
        pendingUninstallPackage = null
        if (pkg != null) {
            onAppUninstallReturned(pkg)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!uiState.appCacheClearQueue.isActive && !uiState.appUninstallQueue.isActive) {
            onRefreshApps()
        }
    }

    if (uiState.appCacheClearQueue.isActive) {
        AppCacheClearQueueDialog(
            queue = uiState.appCacheClearQueue,
            onOpenCurrentAppSettings = { packageName ->
                pendingQueuePackage = packageName
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { appSettingsLauncher.launch(intent) }
                    .onFailure {
                        pendingQueuePackage = null
                        onOpenAppInfo(packageName)
                    }
            },
            onSkipCurrent = onSkipQueueApp,
            onDismiss = onDismissQueue
        )
    }

    if (uiState.appUninstallQueue.isActive) {
        AppUninstallQueueDialog(
            queue = uiState.appUninstallQueue,
            onUninstallCurrentApp = { packageName ->
                pendingUninstallPackage = packageName
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { appUninstallLauncher.launch(intent) }
                    .onFailure {
                        pendingUninstallPackage = null
                        onUninstallApp(packageName)
                    }
            },
            onSkipCurrent = onSkipUninstallApp,
            onDismiss = onDismissUninstallQueue
        )
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مدير التطبيقات والكاش",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${uiState.filteredApps.size} تطبيق مثبت • إجمالي الكاش: ${Formatters.formatBytes(uiState.totalAppsCacheBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("apps_total_cache_summary")
                        )
                    }
                    IconButton(onClick = onRefreshApps) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث قائمة التطبيقات"
                        )
                    }
                }

                // Usage Access Banner when not granted
                if (!uiState.hasUsageAccess) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCard(
                                shape = MaterialTheme.shapes.medium,
                                accentTint = telemetryColors.warning
                            ),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = telemetryColors.warning
                                )
                                Text(
                                    text = "فعّل صلاحية الوصول لإحصائيات الاستخدام",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "لإظهار حجم الذاكرة المؤقتة (Cache) بدقة لكل تطبيق وتنظيفها بشكل فردي، فعّل إذن إحصائيات الاستخدام من إعدادات النظام.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onRequestUsageAccess,
                                modifier = Modifier.testTag("grant_usage_access_button")
                            ) {
                                Text(stringResource(R.string.grant_usage_access))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.appSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("ابحث باسم التطبيق أو الحزمة…") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_search_field")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppSortFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.appSortFilter == filter,
                            onClick = { onSelectSortFilter(filter) },
                            label = { Text(filter.labelAr) }
                        )
                    }
                }

                // Multi-select toolbar row
                if (uiState.filteredApps.isNotEmpty()) {
                    val allFilteredSelected = uiState.filteredApps.all {
                        it.packageName in uiState.selectedAppPackages
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { onSelectAllApps(!allFilteredSelected) },
                            modifier = Modifier.testTag("apps_select_all_button")
                        ) {
                            Text(
                                if (allFilteredSelected) {
                                    "إلغاء تحديد الكل"
                                } else {
                                    "تحديد الكل (${uiState.filteredApps.size})"
                                }
                            )
                        }
                        if (uiState.selectedAppsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "محدد: ${uiState.selectedAppsCount} • ${Formatters.formatBytes(uiState.selectedAppsCacheBytes)}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("apps_selected_badge")
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoadingApps && uiState.installedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "جاري تحليل أحجام التطبيقات والكاش…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apps_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in uiState.selectedAppPackages
                        AppInspectorRow(
                            app = app,
                            isSelected = isSelected,
                            isOwnApp = app.packageName == context.packageName,
                            onToggleSelection = { onToggleAppSelection(app.packageName) },
                            onClearSingleCache = { onClearSingleAppCache(app.packageName) },
                            onOpenInfo = { onOpenAppInfo(app.packageName) },
                            onUninstall = { onUninstallApp(app.packageName) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // Sticky Bottom Bar for batch individual cache cleaning & batch uninstall
            AnimatedVisibility(visible = uiState.selectedAppsCount > 0) {
                val hasUninstallable = uiState.filteredApps.any {
                    it.packageName in uiState.selectedAppPackages && !it.isSystemApp && it.packageName != context.packageName
                }
                val liquidEnabled = LocalLiquidGlassEnabled.current
                Surface(
                    tonalElevation = if (liquidEnabled) 0.dp else 6.dp,
                    shadowElevation = if (liquidEnabled) 0.dp else 8.dp,
                    color = if (liquidEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (liquidEnabled) {
                                Modifier.liquidGlassCard(
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                                    isElevated = true
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("apps_selected_summary"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.selectedAppsCount} تطبيق محدد",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "الكاش المحدد: ${Formatters.formatBytes(uiState.selectedAppsCacheBytes)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = telemetryColors.healthy
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasUninstallable) {
                                OutlinedButton(
                                    onClick = onUninstallSelectedApps,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("uninstall_selected_apps_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إلغاء التثبيت")
                                }
                            }

                            Button(
                                onClick = onClearSelectedCaches,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("clear_selected_caches_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تنظيف كاش المحدد (${uiState.selectedAppsCount})",
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
}

@Composable
private fun AppInspectorRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    isOwnApp: Boolean,
    onToggleSelection: () -> Unit,
    onClearSingleCache: () -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val bitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    val dateFormat = remember { SimpleDateFormat("dd/MM", Locale.US) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidPressBounce()
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = if (isSelected) MaterialTheme.colorScheme.primary else null,
                borderWidth = if (isSelected) 1.5.dp else 1.dp
            )
            .clickable(onClick = onToggleSelection)
            .testTag("app_row_${app.packageName}"),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("app_checkbox_${app.packageName}")
                )

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = app.appName,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isSystemApp) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "نظام",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (app.lastTimeUsedMillis > 0L) {
                            "آخر استخدام: ${dateFormat.format(Date(app.lastTimeUsedMillis))}"
                        } else {
                            app.packageName
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val impactColor = when (app.impactLevel) {
                        AppImpactLevel.HIGH -> telemetryColors.danger
                        AppImpactLevel.MEDIUM -> telemetryColors.warning
                        AppImpactLevel.LOW -> telemetryColors.info
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = impactColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = app.impactLevel.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            color = impactColor,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("app_impact_badge_${app.packageName}")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val cacheLabel = if (app.hasDetailedStats) {
                        "كاش: ${Formatters.formatBytes(app.cacheBytes)}"
                    } else {
                        "كاش: يتطلب الإذن"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            !app.hasDetailedStats -> MaterialTheme.colorScheme.surfaceContainerHigh
                            app.cacheBytes > 25L * 1024L * 1024L -> telemetryColors.warning.copy(alpha = 0.16f)
                            app.cacheBytes > 0L -> telemetryColors.healthy.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = cacheLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = when {
                                !app.hasDetailedStats -> MaterialTheme.colorScheme.onSurfaceVariant
                                app.cacheBytes > 25L * 1024L * 1024L -> telemetryColors.warning
                                app.cacheBytes > 0L -> telemetryColors.healthy
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("app_cache_size_${app.packageName}")
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatters.formatBytes(app.totalBytes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearSingleCache,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_clear_cache_button_${app.packageName}"),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOwnApp) "مسح الكاش مباشرة" else "مسح الكاش",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenInfo,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "إدارة ${app.appName}",
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (!app.isSystemApp && !isOwnApp) {
                    OutlinedButton(
                        onClick = onUninstall,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "إلغاء تثبيت ${app.appName}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onOpenInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "معلومات ${app.appName}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCacheClearQueueDialog(
    queue: AppCacheClearQueueState,
    onOpenCurrentAppSettings: (String) -> Unit,
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
        modifier = Modifier.testTag("app_cache_queue_dialog"),
        title = {
            Text(
                text = if (queue.isComplete) {
                    "اكتمل تنظيف الكاش الفردي"
                } else {
                    "تنظيف كاش التطبيقات (${queue.processedCount + 1} / ${queue.items.size})"
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
                    text = "المساحة المحررة حتى الآن: ${Formatters.formatBytes(queue.totalFreedBytes)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = telemetryColors.healthy
                )

                if (queue.isComplete) {
                    Text(
                        text = "تمت معالجة جميع التطبيقات المحددة بنجاح.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("queue_complete_message")
                    )
                } else if (current != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "التطبيق الحالي: ${current.appName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "اضغط على «فتح إعدادات التخزين» بالأسفل ثم اختر «التخزين وذاكرة التخزين المؤقت» ← «مسح ذاكرة التخزين المؤقت»، وعند العودة سينتقل التطبيق تلقائياً للتالي.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queue.items.forEachIndexed { index, item ->
                        val isCurrent = !queue.isComplete && index == queue.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (item.status) {
                                        AppCacheItemStatus.CLEARED -> Icons.Default.CheckCircle
                                        AppCacheItemStatus.SKIPPED -> Icons.Default.SkipNext
                                        AppCacheItemStatus.PENDING -> Icons.Default.CleaningServices
                                    },
                                    contentDescription = null,
                                    tint = when (item.status) {
                                        AppCacheItemStatus.CLEARED -> telemetryColors.healthy
                                        AppCacheItemStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        AppCacheItemStatus.PENDING -> if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = item.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = when (item.status) {
                                    AppCacheItemStatus.CLEARED -> {
                                        if (item.freedBytes > 0L) {
                                            "-${Formatters.formatBytes(item.freedBytes)}"
                                        } else {
                                            "تم"
                                        }
                                    }
                                    AppCacheItemStatus.SKIPPED -> "تخطي"
                                    AppCacheItemStatus.PENDING -> Formatters.formatBytes(item.initialCacheBytes)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = when (item.status) {
                                    AppCacheItemStatus.CLEARED -> telemetryColors.healthy
                                    AppCacheItemStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    AppCacheItemStatus.PENDING -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (queue.isComplete || current == null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("queue_close_button")
                ) {
                    Text("إنهاء")
                }
            } else {
                Button(
                    onClick = { onOpenCurrentAppSettings(current.packageName) },
                    modifier = Modifier.testTag("queue_open_app_settings_button")
                ) {
                    Text("فتح إعدادات التخزين")
                }
            }
        },
        dismissButton = {
            if (!queue.isComplete && current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSkipCurrent,
                        modifier = Modifier.testTag("queue_skip_app_button")
                    ) {
                        Text("تخطي")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("queue_close_button")
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    )
}

@Composable
private fun AppUninstallQueueDialog(
    queue: AppUninstallQueueState,
    onUninstallCurrentApp: (String) -> Unit,
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
        modifier = Modifier.testTag("app_uninstall_queue_dialog"),
        title = {
            Text(
                text = if (queue.isComplete) {
                    "اكتمل إلغاء التثبيت"
                } else {
                    "إلغاء تثبيت التطبيقات (${queue.processedCount + 1} / ${queue.items.size})"
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
                    text = "تمت إزالة ${queue.uninstalledCount} تطبيق • توفير تقديري: ${Formatters.formatBytes(queue.estimatedFreedBytes)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = telemetryColors.healthy
                )

                if (queue.isComplete) {
                    Text(
                        text = "تمت معالجة قائمة إلغاء التثبيت بنجاح.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("uninstall_queue_complete_message")
                    )
                } else if (current != null) {
                    Text(
                        text = "التطبيق التالي للإزالة: ${current.appName} (${Formatters.formatBytes(current.sizeBytes)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queue.items.forEachIndexed { index, item ->
                        val isCurrent = !queue.isComplete && index == queue.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.appName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when (item.status) {
                                    AppUninstallStatus.UNINSTALLED -> "تمت الإزالة"
                                    AppUninstallStatus.SKIPPED -> "تخطي"
                                    AppUninstallStatus.PENDING -> Formatters.formatBytes(item.sizeBytes)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = when (item.status) {
                                    AppUninstallStatus.UNINSTALLED -> telemetryColors.healthy
                                    AppUninstallStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    AppUninstallStatus.PENDING -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (queue.isComplete || current == null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("uninstall_queue_close_button")
                ) {
                    Text("إنهاء")
                }
            } else {
                Button(
                    onClick = { onUninstallCurrentApp(current.packageName) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("uninstall_queue_confirm_button")
                ) {
                    Text("إزالة ${current.appName}")
                }
            }
        },
        dismissButton = {
            if (!queue.isComplete && current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSkipCurrent,
                        modifier = Modifier.testTag("uninstall_queue_skip_button")
                    ) {
                        Text("تخطي")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("uninstall_queue_close_button")
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    )
}
