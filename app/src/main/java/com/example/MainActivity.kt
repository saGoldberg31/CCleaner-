package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.CleanProViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.liquidGlassCard
import com.example.ui.screens.AppsManagerScreen
import com.example.ui.screens.BatteryHealthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceDashboardScreen
import com.example.ui.screens.FilesAnalyzerScreen
import com.example.ui.screens.PhotoOptimizerScreen
import com.example.ui.screens.ProSettingsScreen
import com.example.ui.screens.SystemMonitorScreen
import com.example.ui.theme.CleanProTheme
import com.example.ui.theme.LocalLiquidGlassEnabled

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_FILES = "files"
const val ROUTE_APPS = "apps"
const val ROUTE_MONITOR = "monitor"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_PHOTO_OPTIMIZER = "photo_optimizer"
const val ROUTE_DEVICE_DASHBOARD = "device_dashboard"
const val ROUTE_BATTERY_HEALTH = "battery_health"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navTabs = listOf(
    NavTab(
        route = ROUTE_DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.CleaningServices,
        unselectedIcon = Icons.Outlined.CleaningServices
    ),
    NavTab(
        route = ROUTE_FILES,
        labelRes = R.string.nav_files,
        selectedIcon = Icons.Filled.FolderCopy,
        unselectedIcon = Icons.Outlined.FolderCopy
    ),
    NavTab(
        route = ROUTE_APPS,
        labelRes = R.string.nav_apps,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps
    ),
    NavTab(
        route = ROUTE_MONITOR,
        labelRes = R.string.nav_monitor,
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory
    ),
    NavTab(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_pro,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CleanProViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.preferences.batterySaverEnabled) {
                val lp = window.attributes
                lp.screenBrightness = if (uiState.preferences.batterySaverEnabled) {
                    0.18f
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = lp
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CleanProTheme(
                    themeMode = uiState.preferences.themeMode,
                    liquidGlassEnabled = uiState.preferences.liquidGlassEnabled,
                    batterySaverEnabled = uiState.preferences.batterySaverEnabled
                ) {
                    CleanProApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CleanProApp(viewModel: CleanProViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onSafFolderAdded(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.scanPickedMediaDuplicates(uris)
            navController.navigate(ROUTE_FILES) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val photoOptimizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.analyzePickedPhotos(uris)
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSystemStorageSettings() {
        val manageIntent = Intent(StorageManager.ACTION_MANAGE_STORAGE)
        val fallbackIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        val chosen = if (manageIntent.resolveActivity(context.packageManager) != null) {
            manageIntent
        } else {
            fallbackIntent
        }
        runCatching { context.startActivity(chosen) }
    }

    fun openUsageAccessSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { context.startActivity(intent) }
    }

    fun uninstallPackage(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { context.startActivity(intent) }
    }

    val liquidEnabled = LocalLiquidGlassEnabled.current

    LiquidGlassBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 700.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (liquidEnabled) Color.Transparent else MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = if (liquidEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                            modifier = if (liquidEnabled) {
                                Modifier.liquidGlassCard(
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    isElevated = true
                                )
                            } else {
                                Modifier
                            }
                        ) {
                            navTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = if (liquidEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .statusBarsPadding()
                                .then(
                                    if (liquidEnabled) {
                                        Modifier.liquidGlassCard(
                                            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                                            isElevated = true
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            navTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        enterTransition = {
                            fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 18 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180)) +
                                slideOutHorizontally(animationSpec = tween(180)) { -it / 24 }
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180)) +
                                slideOutHorizontally(animationSpec = tween(180)) { it / 16 }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                uiState = uiState,
                                onRunDeepScan = { viewModel.runDeepScan() },
                                onCleanSelected = { viewModel.cleanSelectedItems() },
                                onToggleOwnCache = { viewModel.toggleIncludeOwnCache(it) },
                                onAddFolderClick = { safFolderLauncher.launch(null) },
                                onPickPhotosDuplicates = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onBoostMemory = {
                                    viewModel.scanBackgroundProcesses()
                                    navigateToTab(ROUTE_MONITOR)
                                },
                                onOpenSystemStorage = { openSystemStorageSettings() },
                                onOpenFilesTab = { category ->
                                    viewModel.setFileCategoryFilter(category)
                                    navigateToTab(ROUTE_FILES)
                                },
                                onToggleBatterySaver = { viewModel.setBatterySaverEnabled(it) },
                                onToggleLiquidGlass = { viewModel.setLiquidGlassEnabled(it) },
                                onSelectChartMode = { viewModel.setStorageChartMode(it) },
                                onOpenSettings = { navigateToTab(ROUTE_SETTINGS) },
                                onOpenPhotoOptimizer = { navController.navigate(ROUTE_PHOTO_OPTIMIZER) },
                                onOpenDeviceDashboard = { navController.navigate(ROUTE_DEVICE_DASHBOARD) },
                                onOpenBatteryHealth = { navController.navigate(ROUTE_BATTERY_HEALTH) },
                                onClearClipboard = { viewModel.clearClipboard() },
                                onRefreshClipboard = { viewModel.refreshClipboardStatus() },
                                onDismissBanner = { viewModel.dismissBanner() }
                            )
                        }

                    composable(ROUTE_FILES) {
                        FilesAnalyzerScreen(
                            uiState = uiState,
                            onSelectCategoryFilter = { viewModel.setFileCategoryFilter(it) },
                            onToggleFileSelection = { viewModel.toggleFileSelection(it) },
                            onSelectAll = { viewModel.selectAllInCurrentFilter(it) },
                            onCleanSelected = { viewModel.cleanSelectedItems() },
                            onAddFolderClick = { safFolderLauncher.launch(null) },
                            onRescanClick = { viewModel.runDeepScan() },
                            onPickPhotosDuplicates = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }

                    composable(ROUTE_APPS) {
                        AppsManagerScreen(
                            uiState = uiState,
                            onRefreshApps = { viewModel.refreshUsageAccessAndApps() },
                            onRequestUsageAccess = { openUsageAccessSettings() },
                            onSelectSortFilter = { viewModel.setAppSortFilter(it) },
                            onSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                            onOpenAppInfo = { openAppDetails(it) },
                            onUninstallApp = { uninstallPackage(it) },
                            onToggleAppSelection = { viewModel.toggleAppSelection(it) },
                            onSelectAllApps = { viewModel.selectAllFilteredApps(it) },
                            onClearSelectedCaches = { viewModel.startSelectedAppCacheClearing() },
                            onClearSingleAppCache = { viewModel.clearSingleAppCache(it) },
                            onAppSettingsReturned = { viewModel.onAppSettingsReturned(it) },
                            onSkipQueueApp = { viewModel.skipCurrentAppInQueue() },
                            onDismissQueue = { viewModel.dismissAppCacheClearQueue() },
                            onUninstallSelectedApps = { viewModel.startSelectedAppsUninstall() },
                            onAppUninstallReturned = { viewModel.onAppUninstallReturned(it) },
                            onSkipUninstallApp = { viewModel.skipCurrentUninstallApp() },
                            onDismissUninstallQueue = { viewModel.dismissAppUninstallQueue() }
                        )
                    }

                    composable(ROUTE_MONITOR) {
                        SystemMonitorScreen(
                            uiState = uiState,
                            onBoostMemory = { viewModel.boostSelectedMemory() },
                            onClearHistory = { viewModel.clearHistory() },
                            onScanBackgroundProcesses = { viewModel.scanBackgroundProcesses() },
                            onRequestUsageAccess = { openUsageAccessSettings() },
                            onToggleProcessSelection = { viewModel.toggleBackgroundProcessSelection(it) },
                            onSelectAllProcesses = { viewModel.selectAllBackgroundProcesses(it) },
                            onForceStopSingleProcess = { viewModel.attemptSingleBackgroundKill(it) },
                            onStartForceStopQueue = { viewModel.startSelectedForceStopQueue() },
                            onForceStopAppReturned = { viewModel.onForceStopAppReturned(it) },
                            onSkipForceStopApp = { viewModel.skipCurrentForceStopApp() },
                            onDismissForceStopQueue = { viewModel.dismissForceStopQueue() },
                            onDismissBoostResult = { viewModel.dismissMemoryBoostResult() },
                            onOpenDeviceDashboard = { navController.navigate(ROUTE_DEVICE_DASHBOARD) },
                            onOpenBatteryHealth = { navController.navigate(ROUTE_BATTERY_HEALTH) }
                        )
                    }

                    composable(ROUTE_SETTINGS) {
                        ProSettingsScreen(
                            uiState = uiState,
                            onSetThemeMode = { viewModel.setThemeMode(it) },
                            onSetLargeFileThresholdMb = { viewModel.setLargeFileThresholdMb(it) },
                            onSetAutoCleanSchedule = { enabled, frequency, chargingOnly ->
                                viewModel.setAutoCleanSchedule(enabled, frequency, chargingOnly)
                            },
                            onSetBatterySaver = { viewModel.setBatterySaverEnabled(it) },
                            onSetQuickCleanNotification = { viewModel.setQuickCleanNotificationEnabled(it) },
                            onSetLiquidGlass = { viewModel.setLiquidGlassEnabled(it) },
                            onSetCloudSync = { viewModel.setCloudSyncEnabled(it) },
                            onAddSafFolder = { safFolderLauncher.launch(null) },
                            onRemoveSafFolder = { viewModel.removeSafFolder(it) }
                        )
                    }

                    composable(ROUTE_PHOTO_OPTIMIZER) {
                        PhotoOptimizerScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onPickPhotos = {
                                photoOptimizerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSelectPreset = { viewModel.setPhotoCompressionPreset(it) },
                            onTogglePhotoSelection = { viewModel.togglePhotoSelection(it) },
                            onSelectAllPhotos = { viewModel.selectAllPhotos(it) },
                            onCompressSelected = { viewModel.compressSelectedPhotos() }
                        )
                    }

                    composable(ROUTE_DEVICE_DASHBOARD) {
                        DeviceDashboardScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onRunStorageDiagnostics = { viewModel.runStorageHealthDiagnostics() },
                            onOpenMemoryBooster = {
                                navController.popBackStack()
                                navigateToTab(ROUTE_MONITOR)
                            },
                            onOpenBatteryHealth = { navController.navigate(ROUTE_BATTERY_HEALTH) }
                        )
                    }

                    composable(ROUTE_BATTERY_HEALTH) {
                        BatteryHealthScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onRefreshBattery = { viewModel.refreshBatteryHealthSnapshot() },
                            onToggleBatterySaver = { viewModel.setBatterySaverEnabled(it) },
                            onOptimizePowerDrain = { viewModel.boostSelectedMemory() }
                        )
                    }
                }
            }
        }
    }
    }
}
