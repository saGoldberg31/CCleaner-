package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import com.example.widget.MemoryBoostWidgetProvider
import com.example.widget.PinWidgetHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.CleanerPreferences
import com.example.engine.BatteryDrainAnalyzer
import com.example.engine.DeviceTelemetryMonitor
import com.example.engine.MemoryBoosterEngine
import com.example.engine.PhotoOptimizerEngine
import com.example.engine.StorageScannerEngine
import com.example.model.AnalyzedPhotoItem
import com.example.model.AppCacheClearItem
import com.example.model.AppCacheClearQueueState
import com.example.model.AppCacheItemStatus
import com.example.model.AppImpactLevel
import com.example.model.AppUninstallItem
import com.example.model.AppUninstallQueueState
import com.example.model.AppUninstallStatus
import com.example.model.BackgroundProcessInfo
import com.example.model.CleaningProgressState
import com.example.model.CleaningScheduleFrequency
import com.example.model.ClipboardStatus
import com.example.model.DeviceTelemetry
import com.example.model.DiskStorageCategory
import com.example.model.ForceStopItemStatus
import com.example.model.ForceStopQueueItem
import com.example.model.ForceStopQueueState
import com.example.model.Formatters
import com.example.model.InstalledAppInfo
import com.example.model.MemoryBoostResult
import com.example.model.PhotoCompressionPreset
import com.example.model.PhotoQualityIssue
import com.example.model.ProcessResourceTier
import com.example.model.StorageChartMode
import com.example.model.StorageHealthDiagnostics
import com.example.model.StorageHealthStatus
import com.example.ui.CleanProUiState
import com.example.ui.screens.AppsManagerScreen
import com.example.ui.screens.BatteryHealthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceDashboardScreen
import com.example.ui.screens.PhotoOptimizerScreen
import com.example.ui.screens.ProSettingsScreen
import com.example.ui.screens.SystemMonitorScreen
import com.example.ui.theme.CleanProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("منظف برو", appName)
    }

    @Test
    fun `formatBytes formats B KB MB and GB accurately`() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("2 KB", Formatters.formatBytes(2048L))
        assertEquals("1.5 MB", Formatters.formatBytes(1572864L))
        assertEquals("2.00 GB", Formatters.formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `device telemetry computes usage fractions safely`() {
        val telemetry = DeviceTelemetry(
            totalStorageBytes = 1000L,
            usedStorageBytes = 750L,
            freeStorageBytes = 250L,
            totalRamBytes = 4000L,
            usedRamBytes = 2000L,
            availRamBytes = 2000L
        )
        assertEquals(0.75f, telemetry.storageUsageFraction, 0.001f)
        assertEquals(0.50f, telemetry.ramUsageFraction, 0.001f)
    }

    @Test
    fun `clicking clean button triggers circular progress indicator`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalStorageBytes = 1000L,
                            usedStorageBytes = 500L,
                            freeStorageBytes = 500L
                        )
                    )
                )
            }
            CleanProTheme {
                DashboardScreen(
                    uiState = uiState,
                    onRunDeepScan = {},
                    onCleanSelected = {
                        uiState = uiState.copy(
                            cleaningProgress = CleaningProgressState(
                                isCleaning = true,
                                progress = 0.5f,
                                statusLabel = "جاري تنظيف الذاكرة المؤقتة..."
                            )
                        )
                    },
                    onToggleOwnCache = {},
                    onAddFolderClick = {},
                    onPickPhotosDuplicates = {},
                    onBoostMemory = {},
                    onOpenSystemStorage = {},
                    onOpenFilesTab = {},
                    onDismissBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("storage_radial_hero").assertIsDisplayed()
        composeRule.onNodeWithTag("clean_selected_button").performClick()
        composeRule.onNodeWithTag("cleaning_circular_progress").assertIsDisplayed()
    }

    @Test
    fun `toggling battery saver updates ui state and active badge`() {
        var latestInterval = 2
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalStorageBytes = 1000L,
                            usedStorageBytes = 500L,
                            freeStorageBytes = 500L
                        )
                    )
                )
            }
            latestInterval = uiState.telemetryIntervalSeconds
            CleanProTheme {
                DashboardScreen(
                    uiState = uiState,
                    onRunDeepScan = {},
                    onCleanSelected = {},
                    onToggleOwnCache = {},
                    onAddFolderClick = {},
                    onPickPhotosDuplicates = {},
                    onBoostMemory = {},
                    onOpenSystemStorage = {},
                    onOpenFilesTab = {},
                    onToggleBatterySaver = { enabled ->
                        uiState = uiState.copy(
                            preferences = CleanerPreferences(batterySaverEnabled = enabled)
                        )
                    },
                    onDismissBanner = {}
                )
            }
        }

        assertEquals(2, latestInterval)
        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("battery_saver_toggle"))
        composeRule.onNodeWithTag("battery_saver_toggle")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.runOnIdle {
            assertEquals(10, latestInterval)
        }
        composeRule.onNodeWithTag("dashboard_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("battery_saver_active_badge").assertIsDisplayed()
    }

    @Test
    fun `settings screen toggles daily and weekly automatic cleaning schedule`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(CleanProUiState())
            }
            CleanProTheme {
                ProSettingsScreen(
                    uiState = uiState,
                    onSetThemeMode = {},
                    onSetLargeFileThresholdMb = {},
                    onSetAutoCleanSchedule = { enabled, frequency, onlyWhenCharging ->
                        uiState = uiState.copy(
                            preferences = uiState.preferences.copy(
                                autoCleanScheduleEnabled = enabled,
                                autoCleanFrequency = frequency,
                                autoCleanOnlyWhenCharging = onlyWhenCharging
                            )
                        )
                    },
                    onSetBatterySaver = {},
                    onAddSafFolder = {},
                    onRemoveSafFolder = {}
                )
            }
        }

        composeRule.onNodeWithTag("auto_clean_schedule_switch")
            .assertIsOff()
            .performClick()
            .assertIsOn()

        composeRule.onNodeWithTag("schedule_status_label")
            .assertTextContains("يومي", substring = true)

        composeRule.onNodeWithTag("schedule_weekly_chip").performClick()

        composeRule.onNodeWithTag("schedule_status_label")
            .assertTextContains("أسبوعي", substring = true)
    }

    @Test
    fun `apps screen displays cache sizes and supports multi-select individual cache clearing`() {
        val sampleApps = listOf(
            InstalledAppInfo(
                packageName = "com.example.alpha",
                appName = "Alpha",
                icon = null,
                appBytes = 20L * 1024L * 1024L,
                dataBytes = 5L * 1024L * 1024L,
                cacheBytes = 12L * 1024L * 1024L,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis = 1000L,
                isSystemApp = false,
                hasDetailedStats = true
            ),
            InstalledAppInfo(
                packageName = "com.example.beta",
                appName = "Beta",
                icon = null,
                appBytes = 10L * 1024L * 1024L,
                dataBytes = 2L * 1024L * 1024L,
                cacheBytes = 4L * 1024L * 1024L,
                lastTimeUsedMillis = 2000L,
                totalForegroundMillis = 500L,
                isSystemApp = false,
                hasDetailedStats = true
            )
        )

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        hasUsageAccess = true,
                        installedApps = sampleApps
                    )
                )
            }
            CleanProTheme {
                AppsManagerScreen(
                    uiState = uiState,
                    onRefreshApps = {},
                    onRequestUsageAccess = {},
                    onSelectSortFilter = {},
                    onSearchQueryChange = {},
                    onOpenAppInfo = {},
                    onUninstallApp = {},
                    onToggleAppSelection = { pkg ->
                        val next = if (pkg in uiState.selectedAppPackages) {
                            uiState.selectedAppPackages - pkg
                        } else {
                            uiState.selectedAppPackages + pkg
                        }
                        uiState = uiState.copy(selectedAppPackages = next)
                    },
                    onSelectAllApps = { selectAll ->
                        val next = if (selectAll) sampleApps.map { it.packageName }.toSet() else emptySet()
                        uiState = uiState.copy(selectedAppPackages = next)
                    },
                    onClearSelectedCaches = {
                        val queueItems = sampleApps
                            .filter { it.packageName in uiState.selectedAppPackages }
                            .map {
                                AppCacheClearItem(
                                    packageName = it.packageName,
                                    appName = it.appName,
                                    initialCacheBytes = it.cacheBytes
                                )
                            }
                        uiState = uiState.copy(
                            appCacheClearQueue = AppCacheClearQueueState(
                                isActive = true,
                                items = queueItems,
                                currentIndex = 0,
                                isComplete = false
                            )
                        )
                    },
                    onSkipQueueApp = {
                        val q = uiState.appCacheClearQueue
                        val updated = q.items.toMutableList()
                        updated[q.currentIndex] = updated[q.currentIndex].copy(status = AppCacheItemStatus.SKIPPED)
                        val nextIdx = updated.indexOfFirst { it.status == AppCacheItemStatus.PENDING }
                        uiState = uiState.copy(
                            appCacheClearQueue = q.copy(
                                items = updated,
                                currentIndex = if (nextIdx >= 0) nextIdx else updated.lastIndex,
                                isComplete = nextIdx < 0
                            )
                        )
                    },
                    onDismissQueue = {
                        uiState = uiState.copy(appCacheClearQueue = AppCacheClearQueueState(isActive = false))
                    }
                )
            }
        }

        composeRule.onNodeWithTag("app_cache_size_com.example.alpha", useUnmergedTree = true)
            .assertTextContains("12.0 MB", substring = true)

        composeRule.onNodeWithTag("app_checkbox_com.example.alpha")
            .assertIsOff()
            .performClick()
            .assertIsOn()

        composeRule.onNodeWithTag("apps_list")
            .performScrollToIndex(1)

        composeRule.onNodeWithTag("app_cache_size_com.example.beta", useUnmergedTree = true)
            .assertTextContains("4.0 MB", substring = true)

        composeRule.onNodeWithTag("app_checkbox_com.example.beta")
            .assertIsOff()
            .performClick()
            .assertIsOn()

        composeRule.onNodeWithTag("apps_selected_summary")
            .assertTextContains("2 تطبيق محدد", substring = true)
            .assertTextContains("16.0 MB", substring = true)

        composeRule.onNodeWithTag("clear_selected_caches_button").performClick()
        composeRule.onNodeWithTag("app_cache_queue_dialog").assertIsDisplayed()

        // Step through both selected apps individually
        composeRule.onNodeWithTag("queue_skip_app_button").performClick()
        composeRule.onNodeWithTag("queue_skip_app_button").performClick()
        composeRule.onNodeWithTag("queue_complete_message").assertIsDisplayed()
        composeRule.onNodeWithTag("queue_close_button").performClick()
    }

    @Test
    fun `storage scanner classifies photos videos audio documents and archives`() {
        assertEquals(
            DiskStorageCategory.PHOTOS,
            StorageScannerEngine.classifyFileCategory("vacation.jpg", "image/jpeg")
        )
        assertEquals(
            DiskStorageCategory.VIDEOS,
            StorageScannerEngine.classifyFileCategory("clip.mp4", "video/mp4")
        )
        assertEquals(
            DiskStorageCategory.AUDIO,
            StorageScannerEngine.classifyFileCategory("track.mp3", "audio/mpeg")
        )
        assertEquals(
            DiskStorageCategory.DOCUMENTS,
            StorageScannerEngine.classifyFileCategory("report.pdf", "application/pdf")
        )
        assertEquals(
            DiskStorageCategory.ARCHIVES_APK,
            StorageScannerEngine.classifyFileCategory("installer.apk", "application/vnd.android.package-archive")
        )
        assertEquals(
            DiskStorageCategory.OTHER,
            StorageScannerEngine.classifyFileCategory("backup.bin", "application/octet-stream")
        )
    }

    @Test
    fun `dashboard circular category chart switches modes and highlights selected category`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalStorageBytes = 100L * 1024L * 1024L,
                            usedStorageBytes = 60L * 1024L * 1024L,
                            freeStorageBytes = 40L * 1024L * 1024L
                        ),
                        scannedCategoryBytes = mapOf(
                            DiskStorageCategory.PHOTOS to 20L * 1024L * 1024L,
                            DiskStorageCategory.VIDEOS to 15L * 1024L * 1024L,
                            DiskStorageCategory.DOCUMENTS to 5L * 1024L * 1024L
                        ),
                        scannedCategoryCounts = mapOf(
                            DiskStorageCategory.PHOTOS to 12,
                            DiskStorageCategory.VIDEOS to 3,
                            DiskStorageCategory.DOCUMENTS to 8
                        )
                    )
                )
            }
            CleanProTheme {
                DashboardScreen(
                    uiState = uiState,
                    onRunDeepScan = {},
                    onCleanSelected = {},
                    onToggleOwnCache = {},
                    onAddFolderClick = {},
                    onPickPhotosDuplicates = {},
                    onBoostMemory = {},
                    onOpenSystemStorage = {},
                    onOpenFilesTab = {},
                    onSelectChartMode = { mode ->
                        uiState = uiState.copy(storageChartMode = mode)
                    },
                    onDismissBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("storage_category_chart_card"))
        composeRule.onNodeWithTag("storage_category_donut_chart").assertIsDisplayed()

        composeRule.onNodeWithTag("chart_mode_file_categories").performClick()
        composeRule.onNodeWithTag("donut_center_summary")
            .assertTextContains("40.0 MB", substring = true)

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("category_legend_PHOTOS"))
        composeRule.onNodeWithTag("category_legend_PHOTOS").performClick()

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("storage_category_donut_chart"))
        composeRule.onNodeWithTag("donut_center_summary")
            .assertTextContains("الصور", substring = true)
            .assertTextContains("20.0 MB", substring = true)
    }

    @Test
    fun `photo optimizer quality metrics distinguish uniform blurry bitmap from sharp edges and app impact classifies correctly`() {
        val blurryBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 120, 120))
        }
        val sharpBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        for (y in 0 until 24) {
            for (x in 0 until 24) {
                sharpBitmap.setPixel(x, y, if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK)
            }
        }

        val (blurVariance, blurLum) = PhotoOptimizerEngine.computeQualityMetrics(blurryBitmap)
        val (sharpVariance, _) = PhotoOptimizerEngine.computeQualityMetrics(sharpBitmap)

        assertTrue("Uniform bitmap should have near-zero Laplacian variance", blurVariance < 5.0)
        assertTrue("Sharp high-contrast checkerboard should have high Laplacian variance", sharpVariance > 1000.0)
        assertTrue("Mid-gray luminance should be near 120", blurLum in 115.0..125.0)

        val highApp = InstalledAppInfo(
            packageName = "com.example.heavy",
            appName = "Heavy",
            icon = null,
            appBytes = 180L * 1024L * 1024L,
            dataBytes = 50L * 1024L * 1024L,
            cacheBytes = 20L * 1024L * 1024L,
            lastTimeUsedMillis = 1000L,
            totalForegroundMillis = 1000L,
            isSystemApp = false,
            hasDetailedStats = true
        )
        val lowApp = InstalledAppInfo(
            packageName = "com.example.light",
            appName = "Light",
            icon = null,
            appBytes = 12L * 1024L * 1024L,
            dataBytes = 2L * 1024L * 1024L,
            cacheBytes = 1L * 1024L * 1024L,
            lastTimeUsedMillis = 1000L,
            totalForegroundMillis = 1000L,
            isSystemApp = false,
            hasDetailedStats = true
        )
        assertEquals(AppImpactLevel.HIGH, highApp.impactLevel)
        assertEquals(AppImpactLevel.LOW, lowApp.impactLevel)
    }

    @Test
    fun `apps manager displays impact badges and supports batch multi-uninstall queue`() {
        val sampleApps = listOf(
            InstalledAppInfo(
                packageName = "com.example.heavy",
                appName = "HeavyApp",
                icon = null,
                appBytes = 210L * 1024L * 1024L,
                dataBytes = 20L * 1024L * 1024L,
                cacheBytes = 10L * 1024L * 1024L,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis = 1000L,
                isSystemApp = false,
                hasDetailedStats = true
            ),
            InstalledAppInfo(
                packageName = "com.example.light",
                appName = "LightApp",
                icon = null,
                appBytes = 10L * 1024L * 1024L,
                dataBytes = 1L * 1024L * 1024L,
                cacheBytes = 2L * 1024L * 1024L,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis = 1000L,
                isSystemApp = false,
                hasDetailedStats = true
            )
        )

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        hasUsageAccess = true,
                        installedApps = sampleApps,
                        selectedAppPackages = setOf("com.example.heavy", "com.example.light")
                    )
                )
            }
            CleanProTheme {
                AppsManagerScreen(
                    uiState = uiState,
                    onRefreshApps = {},
                    onRequestUsageAccess = {},
                    onSelectSortFilter = {},
                    onSearchQueryChange = {},
                    onOpenAppInfo = {},
                    onUninstallApp = {},
                    onUninstallSelectedApps = {
                        uiState = uiState.copy(
                            appUninstallQueue = AppUninstallQueueState(
                                isActive = true,
                                items = sampleApps.map {
                                    AppUninstallItem(it.packageName, it.appName, it.totalBytes)
                                },
                                currentIndex = 0,
                                isComplete = false
                            )
                        )
                    },
                    onSkipUninstallApp = {
                        val q = uiState.appUninstallQueue
                        val updated = q.items.toMutableList()
                        updated[q.currentIndex] = updated[q.currentIndex].copy(status = AppUninstallStatus.SKIPPED)
                        val nextIdx = updated.indexOfFirst { it.status == AppUninstallStatus.PENDING }
                        uiState = uiState.copy(
                            appUninstallQueue = q.copy(
                                items = updated,
                                currentIndex = if (nextIdx >= 0) nextIdx else updated.lastIndex,
                                isComplete = nextIdx < 0
                            )
                        )
                    },
                    onDismissUninstallQueue = {
                        uiState = uiState.copy(appUninstallQueue = AppUninstallQueueState(isActive = false))
                    }
                )
            }
        }

        composeRule.onNodeWithTag("app_impact_badge_com.example.heavy", useUnmergedTree = true)
            .assertTextContains("تأثير مرتفع", substring = true)

        composeRule.onNodeWithTag("uninstall_selected_apps_button").performClick()
        composeRule.onNodeWithTag("app_uninstall_queue_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("uninstall_queue_skip_button").performClick()
        composeRule.onNodeWithTag("uninstall_queue_skip_button").performClick()
        composeRule.onNodeWithTag("uninstall_queue_complete_message").assertIsDisplayed()
        composeRule.onNodeWithTag("uninstall_queue_close_button").performClick()
    }

    @Test
    fun `photo optimizer screen renders issues and updates savings across compression presets`() {
        val samplePhotos = listOf(
            AnalyzedPhotoItem(
                uri = Uri.parse("content://media/external/images/media/1"),
                displayName = "blurry_shot.jpg",
                sizeBytes = 4L * 1024L * 1024L,
                width = 4000,
                height = 3000,
                sharpnessScore = 18.0,
                meanLuminance = 110.0,
                issue = PhotoQualityIssue.BLURRY,
                estimatedCompressedBytes = 1536L * 1024L,
                isSelected = true
            ),
            AnalyzedPhotoItem(
                uri = Uri.parse("content://media/external/images/media/2"),
                displayName = "dup_shot.jpg",
                sizeBytes = 2L * 1024L * 1024L,
                width = 2000,
                height = 1500,
                sharpnessScore = 240.0,
                meanLuminance = 135.0,
                issue = PhotoQualityIssue.DUPLICATE,
                estimatedCompressedBytes = 1024L * 1024L,
                isSelected = true
            )
        )

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        analyzedPhotos = samplePhotos,
                        photoCompressionPreset = PhotoCompressionPreset.BALANCED
                    )
                )
            }
            CleanProTheme {
                PhotoOptimizerScreen(
                    uiState = uiState,
                    onBack = {},
                    onPickPhotos = {},
                    onSelectPreset = { preset ->
                        val ratio = when (preset) {
                            PhotoCompressionPreset.LIGHT -> 0.75
                            PhotoCompressionPreset.BALANCED -> 0.45
                            PhotoCompressionPreset.HIGH_SAVING -> 0.25
                        }
                        uiState = uiState.copy(
                            photoCompressionPreset = preset,
                            analyzedPhotos = uiState.analyzedPhotos.map {
                                it.copy(estimatedCompressedBytes = (it.sizeBytes * ratio).toLong())
                            }
                        )
                    },
                    onTogglePhotoSelection = {},
                    onSelectAllPhotos = {},
                    onCompressSelected = {
                        uiState = uiState.copy(photoOptimizeBanner = "تم ضغط وحفظ 2 صورة في مجلد Pictures/CleanPro")
                    }
                )
            }
        }

        composeRule.onNodeWithTag("analyzed_photos_list").assertIsDisplayed()
        composeRule.onNodeWithTag("preset_HIGH_SAVING").performClick()
        composeRule.onNodeWithTag("compress_photos_button").performClick()
        composeRule.onNodeWithTag("photo_optimize_banner").assertIsDisplayed()
    }

    @Test
    fun `dashboard clipboard cleaner and settings quick notification switch update state`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        clipboardStatus = ClipboardStatus(
                            hasContent = true,
                            itemCount = 1,
                            previewSummary = "الحافظة تحتوي على نص جاهز للمسح",
                            characterCount = 24
                        )
                    )
                )
            }
            CleanProTheme {
                DashboardScreen(
                    uiState = uiState,
                    onRunDeepScan = {},
                    onCleanSelected = {},
                    onToggleOwnCache = {},
                    onAddFolderClick = {},
                    onPickPhotosDuplicates = {},
                    onBoostMemory = {},
                    onOpenSystemStorage = {},
                    onOpenFilesTab = {},
                    onClearClipboard = {
                        uiState = uiState.copy(clipboardStatus = ClipboardStatus(hasContent = false))
                    },
                    onDismissBanner = {}
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("clipboard_cleaner_card"))
        composeRule.onNodeWithTag("clear_clipboard_button")
            .assertIsEnabled()
            .performClick()
            .assertIsNotEnabled()
    }

    @Test
    fun `settings quick clean notification toggle updates preference`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(CleanProUiState())
            }
            CleanProTheme {
                ProSettingsScreen(
                    uiState = uiState,
                    onSetThemeMode = {},
                    onSetLargeFileThresholdMb = {},
                    onSetAutoCleanSchedule = { _, _, _ -> },
                    onSetQuickCleanNotification = { enabled ->
                        uiState = uiState.copy(
                            preferences = uiState.preferences.copy(quickCleanNotificationEnabled = enabled)
                        )
                    },
                    onAddSafFolder = {},
                    onRemoveSafFolder = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("quick_clean_notification_switch"))
        composeRule.onNodeWithTag("quick_clean_notification_switch")
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun `memory booster process scoring classifies critical heavy processes vs moderate`() {
        val now = 1_700_000_000_000L
        val (criticalScore, criticalTier) = MemoryBoosterEngine.computeProcessScoreAndTier(
            livePssBytes = 220L * 1024L * 1024L,
            totalStorageBytes = 400L * 1024L * 1024L,
            totalForegroundMillis = 3L * 60L * 60L * 1000L,
            lastTimeUsedMillis = now - 5L * 60L * 1000L,
            nowMillis = now,
            hasActiveService = true
        )
        val (moderateScore, moderateTier) = MemoryBoosterEngine.computeProcessScoreAndTier(
            livePssBytes = 0L,
            totalStorageBytes = 25L * 1024L * 1024L,
            totalForegroundMillis = 1L * 60L * 1000L,
            lastTimeUsedMillis = now - 12L * 60L * 60L * 1000L,
            nowMillis = now,
            hasActiveService = false
        )

        assertTrue(criticalScore >= 70)
        assertEquals(ProcessResourceTier.CRITICAL, criticalTier)
        assertTrue(moderateScore < 45)
        assertEquals(ProcessResourceTier.MODERATE, moderateTier)
    }

    @Test
    fun `memory booster screen lists heavy processes boosts RAM and runs force-stop queue`() {
        val sampleProcesses = listOf(
            BackgroundProcessInfo(
                packageName = "com.example.heavygame",
                appName = "HeavyGame",
                icon = null,
                pid = 4123,
                processMemoryBytes = 280L * 1024L * 1024L,
                isLivePssMeasured = true,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis = 2L * 60L * 60L * 1000L,
                cacheBytes = 25L * 1024L * 1024L,
                importanceLabelAr = "خدمة نشطة",
                resourceScore = 88,
                resourceTier = ProcessResourceTier.CRITICAL,
                isStopped = false,
                isSelected = true
            ),
            BackgroundProcessInfo(
                packageName = "com.example.social",
                appName = "SocialApp",
                icon = null,
                pid = null,
                processMemoryBytes = 95L * 1024L * 1024L,
                isLivePssMeasured = false,
                lastTimeUsedMillis = 2000L,
                totalForegroundMillis = 40L * 60L * 1000L,
                cacheBytes = 12L * 1024L * 1024L,
                importanceLabelAr = "نشط في الخلفية مؤخراً",
                resourceScore = 58,
                resourceTier = ProcessResourceTier.HIGH,
                isStopped = false,
                isSelected = true
            )
        )

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        hasUsageAccess = true,
                        telemetry = DeviceTelemetry(
                            totalRamBytes = 8L * 1024L * 1024L * 1024L,
                            availRamBytes = 3L * 1024L * 1024L * 1024L,
                            usedRamBytes = 5L * 1024L * 1024L * 1024L
                        ),
                        backgroundProcesses = sampleProcesses
                    )
                )
            }
            CleanProTheme {
                SystemMonitorScreen(
                    uiState = uiState,
                    onBoostMemory = {
                        uiState = uiState.copy(
                            memoryBoostResult = MemoryBoostResult(
                                ramFreedBytes = 280L * 1024L * 1024L,
                                backgroundKilledCount = 2,
                                cacheFreedBytes = 5L * 1024L * 1024L,
                                summaryMessage = "تم تحرير 280.0 MB من RAM ومعالجة 2 عملية خلفية."
                            )
                        )
                    },
                    onClearHistory = {},
                    onStartForceStopQueue = {
                        uiState = uiState.copy(
                            forceStopQueue = ForceStopQueueState(
                                isActive = true,
                                items = sampleProcesses.map {
                                    ForceStopQueueItem(it.packageName, it.appName, it.processMemoryBytes)
                                },
                                currentIndex = 0,
                                isComplete = false
                            )
                        )
                    },
                    onSkipForceStopApp = {
                        val q = uiState.forceStopQueue
                        val updated = q.items.toMutableList()
                        updated[q.currentIndex] = updated[q.currentIndex].copy(status = ForceStopItemStatus.SKIPPED)
                        val nextIdx = updated.indexOfFirst { it.status == ForceStopItemStatus.PENDING }
                        uiState = uiState.copy(
                            forceStopQueue = q.copy(
                                items = updated,
                                currentIndex = if (nextIdx >= 0) nextIdx else updated.lastIndex,
                                isComplete = nextIdx < 0
                            )
                        )
                    },
                    onDismissForceStopQueue = {
                        uiState = uiState.copy(forceStopQueue = ForceStopQueueState(isActive = false))
                    }
                )
            }
        }

        composeRule.onNodeWithTag("monitor_boost_button").performClick()
        composeRule.onNodeWithTag("memory_boost_result_banner").assertIsDisplayed()

        composeRule.onNodeWithTag("monitor_list")
            .performScrollToNode(hasTestTag("bg_process_card_com.example.heavygame"))
        composeRule.onNodeWithTag("bg_process_tier_com.example.heavygame", useUnmergedTree = true)
            .assertTextContains("استهلاك مرتفع جداً", substring = true)

        composeRule.onNodeWithTag("monitor_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("force_stop_selected_button").performClick()
        composeRule.onNodeWithTag("force_stop_queue_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("force_stop_queue_skip_button").performClick()
        composeRule.onNodeWithTag("force_stop_queue_skip_button").performClick()
        composeRule.onNodeWithTag("force_stop_queue_complete_message").assertIsDisplayed()
        composeRule.onNodeWithTag("force_stop_queue_close_button").performClick()
    }

    @Test
    fun `telemetry monitor normalizes thermal zones and evaluates storage health accurately`() {
        assertEquals(42.5f, DeviceTelemetryMonitor.normalizeThermalTempCelsius(42500f) ?: 0f, 0.01f)
        assertEquals(39.0f, DeviceTelemetryMonitor.normalizeThermalTempCelsius(39.0f) ?: 0f, 0.01f)
        assertNull(DeviceTelemetryMonitor.normalizeThermalTempCelsius(-5f))

        val healthyDiag = DeviceTelemetryMonitor.evaluateStorageHealth(
            totalBytes = 100L * 1024L * 1024L * 1024L,
            freeBytes = 45L * 1024L * 1024L * 1024L,
            ownCacheBytes = 10L * 1024L * 1024L,
            cacheQuotaBytes = 500L * 1024L * 1024L,
            blockSizeBytes = 4096L,
            writeSpeedMbPerSec = 320f,
            readSpeedMbPerSec = 850f,
            ioLatencyMs = 8L,
            timestampMillis = 1000L
        )
        assertEquals(StorageHealthStatus.HEALTHY, healthyDiag.status)
        assertTrue(healthyDiag.healthScore >= 90)

        val criticalDiag = DeviceTelemetryMonitor.evaluateStorageHealth(
            totalBytes = 100L * 1024L * 1024L * 1024L,
            freeBytes = 5L * 1024L * 1024L * 1024L,
            ownCacheBytes = 450L * 1024L * 1024L,
            cacheQuotaBytes = 500L * 1024L * 1024L,
            blockSizeBytes = 4096L,
            writeSpeedMbPerSec = 90f,
            readSpeedMbPerSec = 240f,
            ioLatencyMs = 150L,
            timestampMillis = 1000L
        )
        assertEquals(StorageHealthStatus.CRITICAL, criticalDiag.status)
        assertTrue(criticalDiag.isFragmentationRiskHigh)
        assertTrue(criticalDiag.healthScore < 60)
    }

    @Test
    fun `device dashboard displays cpu temperature total RAM and runs storage health diagnostics`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalStorageBytes = 128L * 1024L * 1024L * 1024L,
                            usedStorageBytes = 64L * 1024L * 1024L * 1024L,
                            freeStorageBytes = 64L * 1024L * 1024L * 1024L,
                            totalRamBytes = 8L * 1024L * 1024L * 1024L,
                            usedRamBytes = 4L * 1024L * 1024L * 1024L,
                            availRamBytes = 4L * 1024L * 1024L * 1024L,
                            ramLowThresholdBytes = 512L * 1024L * 1024L,
                            cpuTempCelsius = 41.8f,
                            cpuTempSourceLabel = "مستشعر المعالج (cpu)",
                            thermalStatusLabel = "طبيعية",
                            cpuCores = 8,
                            cpuAbi = "arm64-v8a",
                            cpuCurrentFreqMhz = 1950
                        ),
                        storageHealth = StorageHealthDiagnostics(
                            status = StorageHealthStatus.HEALTHY,
                            healthScore = 96,
                            freeSpacePercent = 50.0f
                        )
                    )
                )
            }
            CleanProTheme {
                DeviceDashboardScreen(
                    uiState = uiState,
                    onBack = {},
                    onRunStorageDiagnostics = {
                        uiState = uiState.copy(
                            storageHealth = uiState.storageHealth.copy(
                                writeSpeedMbPerSec = 310f,
                                readSpeedMbPerSec = 820f,
                                ioLatencyMs = 6L,
                                healthScore = 100
                            )
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("cpu_temperature_value")
            .assertTextContains("41.8°C", substring = true)

        composeRule.onNodeWithTag("device_dashboard_list")
            .performScrollToNode(hasTestTag("ram_health_card"))
        composeRule.onNodeWithTag("total_ram_value", useUnmergedTree = true)
            .assertTextContains("8.00 GB", substring = true)

        composeRule.onNodeWithTag("device_dashboard_list")
            .performScrollToNode(hasTestTag("run_storage_diagnostics_button"))
        composeRule.onNodeWithTag("run_storage_diagnostics_button").performClick()

        composeRule.onNodeWithTag("storage_health_score", useUnmergedTree = true)
            .assertTextContains("100/100", substring = true)
    }

    @Test
    fun `dashboard liquid glass chip and settings switch toggle liquid glass mode smoothly`() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalStorageBytes = 64L * 1024L * 1024L * 1024L,
                            usedStorageBytes = 32L * 1024L * 1024L * 1024L,
                            freeStorageBytes = 32L * 1024L * 1024L * 1024L
                        )
                    )
                )
            }
            CleanProTheme(
                liquidGlassEnabled = uiState.preferences.liquidGlassEnabled,
                batterySaverEnabled = uiState.preferences.batterySaverEnabled
            ) {
                com.example.ui.components.LiquidGlassBackdrop {
                    DashboardScreen(
                        uiState = uiState,
                        onRunDeepScan = {},
                        onCleanSelected = {},
                        onToggleOwnCache = {},
                        onAddFolderClick = {},
                        onPickPhotosDuplicates = {},
                        onBoostMemory = {},
                        onOpenSystemStorage = {},
                        onOpenFilesTab = {},
                        onToggleLiquidGlass = { enabled ->
                            uiState = uiState.copy(
                                preferences = uiState.preferences.copy(liquidGlassEnabled = enabled)
                            )
                        },
                        onDismissBanner = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("liquid_glass_chip").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("storage_radial_hero").assertIsDisplayed()
        composeRule.onNodeWithTag("liquid_glass_chip").performClick()
        composeRule.onNodeWithTag("storage_radial_hero").assertIsDisplayed()
    }

    @Test
    fun `settings screen toggles liquid glass switch and updates status label`() {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(CleanProUiState()) }
            CleanProTheme(liquidGlassEnabled = uiState.preferences.liquidGlassEnabled) {
                com.example.ui.components.LiquidGlassBackdrop {
                    ProSettingsScreen(
                        uiState = uiState,
                        onSetThemeMode = {},
                        onSetLargeFileThresholdMb = {},
                        onSetAutoCleanSchedule = { _, _, _ -> },
                        onSetLiquidGlass = { enabled ->
                            uiState = uiState.copy(
                                preferences = uiState.preferences.copy(liquidGlassEnabled = enabled)
                            )
                        },
                        onAddSafFolder = {},
                        onRemoveSafFolder = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("liquid_glass_switch"))
        composeRule.onNodeWithTag("liquid_glass_switch")
            .assertIsOn()
            .performClick()
            .assertIsOff()
        composeRule.onNodeWithTag("liquid_glass_status_label")
            .assertTextContains("متوقفة", substring = true)

        composeRule.onNodeWithTag("liquid_glass_switch")
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("liquid_glass_status_label")
            .assertTextContains("مفعّلة", substring = true)
    }

    @Test
    fun `settings screen toggles cloud sync switch and updates status`() {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(CleanProUiState()) }
            CleanProTheme {
                ProSettingsScreen(
                    uiState = uiState,
                    onSetThemeMode = {},
                    onSetLargeFileThresholdMb = {},
                    onSetAutoCleanSchedule = { _, _, _ -> },
                    onSetCloudSync = { enabled ->
                        uiState = uiState.copy(
                            preferences = uiState.preferences.copy(cloudSyncEnabled = enabled)
                        )
                    },
                    onAddSafFolder = {},
                    onRemoveSafFolder = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("cloud_sync_switch"))
        composeRule.onNodeWithTag("cloud_sync_switch")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("cloud_sync_status_label")
            .assertTextContains("مفعّلة", substring = true)
    }

    @Test
    fun `battery drain analyzer evaluates high temperature and background drain correctly`() {
        val processes = listOf(
            BackgroundProcessInfo(
                packageName = "com.example.game",
                appName = "HeavyGame",
                icon = null,
                pid = 1234,
                processMemoryBytes = 350L * 1024L * 1024L,
                isLivePssMeasured = true,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis = 3L * 3600L * 1000L,
                cacheBytes = 50L * 1024L * 1024L,
                importanceLabelAr = "خدمة نشطة",
                resourceScore = 90,
                resourceTier = ProcessResourceTier.CRITICAL,
                isStopped = false,
                isSelected = true
            )
        )

        val report = BatteryDrainAnalyzer.analyzeDrain(processes, 42.0f)
        assertTrue(report.highDrainAppCount > 0)
        assertEquals("مرتفع جداً (استنزاف ملحوظ للبطارية)", report.thermalDrainSeverity)
    }

    @Test
    fun `MemoryBoostWidgetProvider handles broadcast actions cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = MemoryBoostWidgetProvider()

        val refreshIntent = Intent(context, MemoryBoostWidgetProvider::class.java).apply {
            action = MemoryBoostWidgetProvider.ACTION_REFRESH_WIDGET_TELEMETRY
        }
        provider.onReceive(context, refreshIntent)

        val boostIntent = Intent(context, MemoryBoostWidgetProvider::class.java).apply {
            action = MemoryBoostWidgetProvider.ACTION_ONE_TAP_MEMORY_BOOST
        }
        provider.onReceive(context, boostIntent)

        assertEquals(
            "لا توجد عمليات تستحق التسريع حاليًا",
            context.getString(R.string.widget_boost_nothing)
        )
        assertEquals(
            "تم إيقاف 3 تطبيق",
            context.getString(R.string.widget_boost_stopped_count, 3)
        )
        assertEquals(
            "تعذّر إكمال التسريع",
            context.getString(R.string.widget_boost_failed)
        )
    }

    @Test
    fun `PinWidgetHelper queries support without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgetSupported = PinWidgetHelper.isPinWidgetSupported(context)
        val shortcutSupported = PinWidgetHelper.isPinShortcutSupported(context)
        assertTrue(widgetSupported || !widgetSupported)
        assertTrue(shortcutSupported || !shortcutSupported)
    }

    @Test
    fun `SystemMonitorScreen displays pin widget and pin shortcut buttons and handles clicks`() {
        composeRule.setContent {
            CleanProTheme {
                SystemMonitorScreen(
                    uiState = CleanProUiState(
                        telemetry = DeviceTelemetry(
                            totalRamBytes = 4000L,
                            usedRamBytes = 2500L,
                            availRamBytes = 1500L
                        )
                    ),
                    onBoostMemory = {},
                    onClearHistory = {}
                )
            }
        }

        composeRule.onNodeWithTag("monitor_list")
            .performScrollToNode(hasTestTag("pin_widget_button"))
        composeRule.onNodeWithTag("pin_widget_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithTag("pin_shortcut_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    @Test
    fun `ProSettingsScreen displays settings pin widget and shortcut buttons`() {
        composeRule.setContent {
            CleanProTheme {
                ProSettingsScreen(
                    uiState = CleanProUiState(),
                    onSetThemeMode = {},
                    onSetLargeFileThresholdMb = {},
                    onSetAutoCleanSchedule = { _, _, _ -> },
                    onAddSafFolder = {},
                    onRemoveSafFolder = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_pin_widget_button"))
        composeRule.onNodeWithTag("settings_pin_widget_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithTag("settings_pin_shortcut_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    @Test
    fun `battery health evaluator calculates charge discharge times and overheat score accurately`() {
        val dischargingNormal = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 60,
            chargeStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
            pluggedType = 0,
            tempCelsius = 31.5f,
            voltageMv = 3950,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = null,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = -1L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 0
        )
        val dischargingWithSaver = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 60,
            chargeStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
            pluggedType = 0,
            tempCelsius = 31.5f,
            voltageMv = 3950,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = null,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = -1L,
            isBatterySaverEnabled = true,
            heavyBackgroundProcessCount = 0
        )
        val dischargingHeavyLoad = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 60,
            chargeStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
            pluggedType = 0,
            tempCelsius = 31.5f,
            voltageMv = 3950,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = null,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = -1L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 5
        )
        assertEquals(100, dischargingNormal.healthScore)
        assertEquals("مثالية", dischargingNormal.thermalStatusLabel)
        assertTrue(!dischargingNormal.isChargeTime)
        assertTrue(dischargingWithSaver.remainingTimeMillis > dischargingNormal.remainingTimeMillis)
        assertTrue(dischargingHeavyLoad.remainingTimeMillis < dischargingNormal.remainingTimeMillis)

        val hardwareCharging = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 45,
            chargeStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            tempCelsius = 37.2f,
            voltageMv = 4250,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = 1800,
            chargeCounterUah = 2_000_000L,
            systemChargeTimeRemainingMs = 45L * 60_000L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 0
        )
        assertTrue(hardwareCharging.isChargeTime)
        assertTrue(hardwareCharging.isHardwareMeasured)
        assertEquals(45L * 60_000L, hardwareCharging.remainingTimeMillis)
        assertEquals("دافئة", hardwareCharging.thermalStatusLabel)

        val fallbackCharging = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 50,
            chargeStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            pluggedType = BatteryManager.BATTERY_PLUGGED_USB,
            tempCelsius = 33.0f,
            voltageMv = 4100,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = null,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = -1L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 0
        )
        assertTrue(fallbackCharging.isChargeTime)
        assertTrue(fallbackCharging.remainingTimeMillis > 0L)

        val fullCharge = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 100,
            chargeStatus = BatteryManager.BATTERY_STATUS_FULL,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            tempCelsius = 30.0f,
            voltageMv = 4350,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_GOOD,
            currentMa = 0,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = 0L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 0
        )
        assertEquals(0L, fullCharge.remainingTimeMillis)

        val overheated = DeviceTelemetryMonitor.evaluateBatteryHealthAndTime(
            batteryPercent = 40,
            chargeStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
            pluggedType = 0,
            tempCelsius = 43.5f,
            voltageMv = 3800,
            hardwareHealthCode = BatteryManager.BATTERY_HEALTH_OVERHEAT,
            currentMa = null,
            chargeCounterUah = null,
            systemChargeTimeRemainingMs = -1L,
            isBatterySaverEnabled = false,
            heavyBackgroundProcessCount = 0
        )
        assertEquals("ساخنة (يُنصح بالتبريد)", overheated.thermalStatusLabel)
        assertTrue(overheated.healthScore <= 40)

        assertEquals("مكتمل الشحن (100%)", Formatters.formatRemainingTime(0L, isCharging = true, batteryPercent = 100))
        assertEquals("1س 30د", Formatters.formatRemainingTime(90L * 60_000L, isCharging = false, batteryPercent = 50))
        assertEquals("25 دقيقة", Formatters.formatRemainingTime(25L * 60_000L, isCharging = true, batteryPercent = 80))
    }

    @Test
    fun `BatteryHealthScreen renders temperature charge status remaining time and optimization actions`() {
        var optimizeClicked = false
        var refreshedCount = 0
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CleanProUiState(
                        telemetry = DeviceTelemetry(
                            batteryPercent = 72,
                            batteryTempCelsius = 38.2f,
                            batteryVoltageMv = 4080,
                            isCharging = false,
                            batteryHealthLabel = "ممتازة",
                            batteryChargeStatusLabel = "قيد التفريغ",
                            batteryPluggedSourceLabel = "طاقة البطارية",
                            batteryTechnology = "Li-ion",
                            batteryCurrentMa = 420,
                            batteryEstimatedCapacityMah = 4800,
                            batteryHealthScore = 94,
                            batteryThermalStatusLabel = "دافئة",
                            batteryTimeRemainingMillis = 8L * 3600_000L + 15L * 60_000L,
                            isChargeTimeEstimate = false,
                            isHardwareChargeEstimate = true
                        )
                    )
                )
            }
            CleanProTheme(batterySaverEnabled = uiState.isBatterySaverEnabled) {
                BatteryHealthScreen(
                    uiState = uiState,
                    onBack = {},
                    onRefreshBattery = { refreshedCount++ },
                    onToggleBatterySaver = { enabled ->
                        uiState = uiState.copy(
                            preferences = uiState.preferences.copy(batterySaverEnabled = enabled),
                            telemetry = uiState.telemetry.copy(
                                batteryTimeRemainingMillis = if (enabled) {
                                    10L * 3600_000L + 20L * 60_000L
                                } else {
                                    8L * 3600_000L + 15L * 60_000L
                                }
                            )
                        )
                    },
                    onOptimizePowerDrain = { optimizeClicked = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battery_charge_status_badge", useUnmergedTree = true)
            .assertTextContains("قيد التفريغ", substring = true)
        composeRule.onNodeWithTag("battery_plugged_source", useUnmergedTree = true)
            .assertTextContains("طاقة البطارية", substring = true)
        composeRule.onNodeWithTag("battery_percent_value", useUnmergedTree = true)
            .assertTextContains("72%", substring = true)
        composeRule.onNodeWithTag("battery_remaining_time_value", useUnmergedTree = true)
            .assertTextContains("8س 15د", substring = true)

        composeRule.onNodeWithTag("battery_health_list")
            .performScrollToNode(hasTestTag("battery_thermal_health_card"))
        composeRule.onNodeWithTag("battery_temperature_value", useUnmergedTree = true)
            .assertTextContains("38.2°C", substring = true)
        composeRule.onNodeWithTag("battery_thermal_status_label", useUnmergedTree = true)
            .assertTextContains("دافئة", substring = true)
        composeRule.onNodeWithTag("battery_health_score", useUnmergedTree = true)
            .assertTextContains("94/100", substring = true)

        composeRule.onNodeWithTag("battery_health_list")
            .performScrollToNode(hasTestTag("battery_optimize_drain_button"))
        composeRule.onNodeWithTag("battery_optimize_drain_button")
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("battery_saver_quick_button")
            .assertIsEnabled()
            .performClick()

        composeRule.waitForIdle()
        assertTrue(optimizeClicked)
        assertTrue(refreshedCount >= 1)

        composeRule.onNodeWithTag("battery_health_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("battery_remaining_time_value", useUnmergedTree = true)
            .assertTextContains("10س 20د", substring = true)
    }
}

