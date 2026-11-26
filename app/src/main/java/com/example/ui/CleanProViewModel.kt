package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CleanerPreferences
import com.example.data.CleanerPreferencesRepository
import com.example.data.local.CleanHistoryEntity
import com.example.data.local.CleanProDatabase
import com.example.engine.AppAnalyzerEngine
import com.example.engine.ClipboardCleanerHelper
import com.example.engine.DeviceTelemetryMonitor
import com.example.engine.MemoryBoosterEngine
import com.example.engine.PhotoOptimizerEngine
import com.example.engine.StorageScannerEngine
import com.example.model.AnalyzedPhotoItem
import com.example.model.AppCacheClearItem
import com.example.model.AppCacheClearQueueState
import com.example.model.AppCacheItemStatus
import com.example.model.AppSortFilter
import com.example.model.AppUninstallItem
import com.example.model.AppUninstallQueueState
import com.example.model.AppUninstallStatus
import com.example.model.BackgroundProcessInfo
import com.example.model.CleanSummaryBanner
import com.example.model.CleaningProgressState
import com.example.model.CleaningScheduleFrequency
import com.example.model.ClipboardStatus
import com.example.model.DeviceTelemetry
import com.example.model.DiskCategoryUsage
import com.example.model.DiskStorageCategory
import com.example.model.FileCategory
import com.example.model.ForceStopItemStatus
import com.example.model.ForceStopQueueItem
import com.example.model.ForceStopQueueState
import com.example.model.Formatters
import com.example.model.InstalledAppInfo
import com.example.model.MemoryBoostResult
import com.example.model.PhotoCompressionPreset
import com.example.model.SafFolderInfo
import com.example.model.ScanProgressState
import com.example.model.ScannedFileItem
import com.example.model.StorageBreakdownResult
import com.example.model.StorageChartMode
import com.example.model.StorageHealthDiagnostics
import com.example.scheduler.QuickCleanNotificationManager
import com.example.scheduler.ReminderScheduler
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanProUiState(
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val preferences: CleanerPreferences = CleanerPreferences(),
    val safFolders: List<SafFolderInfo> = emptyList(),
    val ownCacheBytes: Long = 0L,
    val includeOwnCacheInClean: Boolean = true,
    val scannedFiles: List<ScannedFileItem> = emptyList(),
    val scannedCategoryBytes: Map<DiskStorageCategory, Long> = emptyMap(),
    val scannedCategoryCounts: Map<DiskStorageCategory, Int> = emptyMap(),
    val storageChartMode: StorageChartMode = StorageChartMode.FULL_DISK,
    val selectedCategoryFilter: FileCategory? = null,
    val scanProgress: ScanProgressState = ScanProgressState(),
    val cleaningProgress: CleaningProgressState = CleaningProgressState(),
    val hasCompletedInitialScan: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val selectedAppPackages: Set<String> = emptySet(),
    val appCacheClearQueue: AppCacheClearQueueState = AppCacheClearQueueState(),
    val appUninstallQueue: AppUninstallQueueState = AppUninstallQueueState(),
    val hasUsageAccess: Boolean = false,
    val isLoadingApps: Boolean = false,
    val appSortFilter: AppSortFilter = AppSortFilter.MOST_CACHE,
    val appSearchQuery: String = "",
    val clipboardStatus: ClipboardStatus = ClipboardStatus(),
    val analyzedPhotos: List<AnalyzedPhotoItem> = emptyList(),
    val photoCompressionPreset: PhotoCompressionPreset = PhotoCompressionPreset.BALANCED,
    val isAnalyzingPhotos: Boolean = false,
    val isCompressingPhotos: Boolean = false,
    val photoOptimizeBanner: String? = null,
    val backgroundProcesses: List<BackgroundProcessInfo> = emptyList(),
    val isScanningProcesses: Boolean = false,
    val isBoostingMemory: Boolean = false,
    val memoryBoostResult: MemoryBoostResult? = null,
    val forceStopQueue: ForceStopQueueState = ForceStopQueueState(),
    val storageHealth: StorageHealthDiagnostics = StorageHealthDiagnostics(),
    val isRunningStorageDiagnostics: Boolean = false,
    val history: List<CleanHistoryEntity> = emptyList(),
    val totalFreedEverBytes: Long = 0L,
    val cleanBanner: CleanSummaryBanner? = null
) {
    val activeHeavyProcessesCount: Int
        get() = backgroundProcesses.count { !it.isStopped }

    val selectedProcessesCount: Int
        get() = backgroundProcesses.count { it.isSelected && !it.isStopped }

    val selectedProcessesMemoryBytes: Long
        get() = backgroundProcesses.filter { it.isSelected && !it.isStopped }.sumOf { it.processMemoryBytes }

    val selectedFilesBytes: Long
        get() = scannedFiles.filter { it.isSelected && it.isDeletableSaf }.sumOf { it.sizeBytes }

    val selectedFilesCount: Int
        get() = scannedFiles.count { it.isSelected && it.isDeletableSaf }

    val totalCleanableBytes: Long
        get() = selectedFilesBytes + if (includeOwnCacheInClean) ownCacheBytes else 0L

    val totalAppsCacheBytes: Long
        get() = installedApps.sumOf { it.cacheBytes }

    val selectedAppsCount: Int
        get() = selectedAppPackages.size

    val selectedAppsCacheBytes: Long
        get() = installedApps.filter { it.packageName in selectedAppPackages }.sumOf { it.cacheBytes }

    val selectedPhotosCount: Int
        get() = analyzedPhotos.count { it.isSelected }

    val estimatedPhotoSavingsBytes: Long
        get() = analyzedPhotos.filter { it.isSelected }.sumOf { it.estimatedSavedBytes }

    val diskCategorySlices: List<DiskCategoryUsage>
        get() {
            val photos = scannedCategoryBytes[DiskStorageCategory.PHOTOS] ?: 0L
            val videos = scannedCategoryBytes[DiskStorageCategory.VIDEOS] ?: 0L
            val audio = scannedCategoryBytes[DiskStorageCategory.AUDIO] ?: 0L
            val documents = scannedCategoryBytes[DiskStorageCategory.DOCUMENTS] ?: 0L
            val archives = scannedCategoryBytes[DiskStorageCategory.ARCHIVES_APK] ?: 0L
            val scannedOther = scannedCategoryBytes[DiskStorageCategory.OTHER] ?: 0L

            return if (storageChartMode == StorageChartMode.FILE_CATEGORIES) {
                listOf(
                    DiskCategoryUsage(DiskStorageCategory.PHOTOS, photos, scannedCategoryCounts[DiskStorageCategory.PHOTOS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.VIDEOS, videos, scannedCategoryCounts[DiskStorageCategory.VIDEOS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.AUDIO, audio, scannedCategoryCounts[DiskStorageCategory.AUDIO] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.DOCUMENTS, documents, scannedCategoryCounts[DiskStorageCategory.DOCUMENTS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.ARCHIVES_APK, archives, scannedCategoryCounts[DiskStorageCategory.ARCHIVES_APK] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.OTHER, scannedOther, scannedCategoryCounts[DiskStorageCategory.OTHER] ?: 0)
                )
            } else {
                val appsTotal = installedApps.sumOf { it.totalBytes }
                val categorizedMediaAndDocs = photos + videos + audio + documents + archives
                val remainingSystemAndOther = (telemetry.usedStorageBytes - appsTotal - categorizedMediaAndDocs)
                    .coerceAtLeast(scannedOther)

                listOf(
                    DiskCategoryUsage(DiskStorageCategory.PHOTOS, photos, scannedCategoryCounts[DiskStorageCategory.PHOTOS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.VIDEOS, videos, scannedCategoryCounts[DiskStorageCategory.VIDEOS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.AUDIO, audio, scannedCategoryCounts[DiskStorageCategory.AUDIO] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.DOCUMENTS, documents, scannedCategoryCounts[DiskStorageCategory.DOCUMENTS] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.ARCHIVES_APK, archives, scannedCategoryCounts[DiskStorageCategory.ARCHIVES_APK] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.APPS, appsTotal, installedApps.size),
                    DiskCategoryUsage(DiskStorageCategory.OTHER, remainingSystemAndOther, scannedCategoryCounts[DiskStorageCategory.OTHER] ?: 0),
                    DiskCategoryUsage(DiskStorageCategory.FREE, telemetry.freeStorageBytes, 0)
                )
            }
        }

    val isBatterySaverEnabled: Boolean
        get() = preferences.batterySaverEnabled

    val telemetryIntervalSeconds: Int
        get() = if (preferences.batterySaverEnabled) 10 else 2

    val filteredFiles: List<ScannedFileItem>
        get() = if (selectedCategoryFilter == null) {
            scannedFiles
        } else {
            scannedFiles.filter { it.category == selectedCategoryFilter }
        }

    val filteredApps: List<InstalledAppInfo>
        get() {
            val query = appSearchQuery.trim()
            val searched = if (query.isEmpty()) {
                installedApps
            } else {
                installedApps.filter {
                    it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            return when (appSortFilter) {
                AppSortFilter.MOST_CACHE -> searched.sortedByDescending { it.cacheBytes }
                AppSortFilter.LARGEST_SIZE -> searched.sortedByDescending { it.totalBytes }
                AppSortFilter.LEAST_USED -> searched.sortedBy { it.lastTimeUsedMillis }
                AppSortFilter.ALL_AZ -> searched.sortedBy { it.appName.lowercase() }
            }
        }
}

class CleanProViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = CleanProDatabase.getInstance(appContext)
    private val prefsRepo = CleanerPreferencesRepository(appContext, db.cleanHistoryDao())
    private val telemetryMonitor = DeviceTelemetryMonitor(appContext)
    private val appAnalyzer = AppAnalyzerEngine(appContext)
    private val memoryBooster = MemoryBoosterEngine(appContext, appAnalyzer)
    private val storageScanner = StorageScannerEngine(appContext)
    private val photoOptimizer = PhotoOptimizerEngine(appContext)

    private val internalState = MutableStateFlow(CleanProUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val telemetryFlow = prefsRepo.preferencesFlow
        .map { it.batterySaverEnabled }
        .distinctUntilChanged()
        .flatMapLatest { batterySaver ->
            telemetryMonitor.observeTelemetry(
                intervalMs = if (batterySaver) 10_000L else 2_000L,
                isBatterySaverProvider = { batterySaver },
                heavyProcessCountProvider = { internalState.value.activeHeavyProcessesCount }
            )
        }

    val uiState: StateFlow<CleanProUiState> = combine(
        internalState,
        telemetryFlow,
        prefsRepo.preferencesFlow,
        prefsRepo.historyFlow,
        prefsRepo.totalFreedFlow
    ) { state, telemetry, prefs, history, totalFreed ->
        val resolvedFolders = storageScanner.resolveSafFolders(prefs.safFolderUris)
        state.copy(
            telemetry = telemetry,
            preferences = prefs,
            safFolders = resolvedFolders,
            history = history,
            totalFreedEverBytes = totalFreed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CleanProUiState(telemetry = telemetryMonitor.readSnapshot())
    )

    init {
        refreshOwnCache()
        refreshUsageAccessAndApps()
        refreshClipboardStatus()
        scanBackgroundProcesses()
        runStorageHealthDiagnostics()
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.quickCleanNotificationEnabled) {
                QuickCleanNotificationManager.showOrUpdate(appContext)
            }
        }
    }

    fun refreshBatteryHealthSnapshot() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            val snapshot = withContext(Dispatchers.IO) {
                telemetryMonitor.readSnapshot(
                    isBatterySaverEnabled = prefs.batterySaverEnabled,
                    heavyBackgroundProcessCount = internalState.value.activeHeavyProcessesCount
                )
            }
            internalState.update { it.copy(telemetry = snapshot) }
        }
        scanBackgroundProcesses()
    }

    fun runStorageHealthDiagnostics() {
        if (internalState.value.isRunningStorageDiagnostics) return
        viewModelScope.launch {
            internalState.update { it.copy(isRunningStorageDiagnostics = true) }
            val ownCache = appAnalyzer.getOwnCacheBytes()
            val health = telemetryMonitor.runStorageHealthDiagnostics(ownCache)
            val snapshot = telemetryMonitor.readSnapshot(
                isBatterySaverEnabled = internalState.value.preferences.batterySaverEnabled,
                heavyBackgroundProcessCount = internalState.value.activeHeavyProcessesCount
            )
            internalState.update {
                it.copy(
                    telemetry = snapshot,
                    ownCacheBytes = ownCache,
                    storageHealth = health,
                    isRunningStorageDiagnostics = false
                )
            }
        }
    }

    fun refreshOwnCache() {
        viewModelScope.launch {
            val cache = appAnalyzer.getOwnCacheBytes()
            internalState.update { it.copy(ownCacheBytes = cache) }
        }
    }

    fun refreshUsageAccessAndApps() {
        viewModelScope.launch {
            val access = appAnalyzer.hasUsageAccess()
            internalState.update { it.copy(hasUsageAccess = access, isLoadingApps = true) }
            val apps = appAnalyzer.analyzeInstalledApps()
            internalState.update {
                it.copy(
                    installedApps = apps,
                    hasUsageAccess = access,
                    isLoadingApps = false
                )
            }
        }
        scanBackgroundProcesses()
    }

    fun onSafFolderAdded(uri: Uri) {
        viewModelScope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, flags)
            }.onFailure {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            prefsRepo.addSafFolderUri(uri.toString())
            runDeepScan()
        }
    }

    fun removeSafFolder(uriString: String) {
        viewModelScope.launch {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
            }
            prefsRepo.removeSafFolderUri(uriString)
            runDeepScan()
        }
    }

    fun runDeepScan() {
        if (internalState.value.scanProgress.isScanning) return
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    scanProgress = ScanProgressState(
                        isScanning = true,
                        currentStepLabel = "جاري قياس الذاكرة المؤقتة للتطبيق...",
                        filesScannedCount = 0
                    ),
                    cleanBanner = null
                )
            }

            val ownCache = appAnalyzer.getOwnCacheBytes()
            val prefs = prefsRepo.preferencesFlow.first()
            val validUris = storageScanner.resolveSafFolders(prefs.safFolderUris)
                .filter { it.hasValidPermission }
                .map { it.uriString }
                .toSet()

            val breakdown = if (validUris.isNotEmpty()) {
                storageScanner.scanSafFoldersWithBreakdown(
                    folderUris = validUris,
                    largeFileThresholdMb = prefs.largeFileThresholdMb,
                    onProgress = { count, label ->
                        internalState.update { state ->
                            state.copy(
                                scanProgress = ScanProgressState(
                                    isScanning = true,
                                    currentStepLabel = label,
                                    filesScannedCount = count
                                )
                            )
                        }
                    }
                )
            } else {
                StorageBreakdownResult()
            }

            prefsRepo.updateLastScanTimestamp(System.currentTimeMillis())

            internalState.update {
                it.copy(
                    ownCacheBytes = ownCache,
                    scannedFiles = breakdown.scannedItems,
                    scannedCategoryBytes = breakdown.categoryBytes,
                    scannedCategoryCounts = breakdown.categoryCounts,
                    scanProgress = ScanProgressState(isScanning = false),
                    hasCompletedInitialScan = true
                )
            }
        }
    }

    fun scanPickedMediaDuplicates(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    scanProgress = ScanProgressState(
                        isScanning = true,
                        currentStepLabel = "فحص تطابق الصور عبر SHA-256...",
                        filesScannedCount = uris.size
                    )
                )
            }
            val mediaDuplicates = storageScanner.findPickedMediaDuplicates(uris)
            internalState.update { state ->
                val nonMedia = state.scannedFiles.filterNot { it.id.startsWith("media_dup_") }
                state.copy(
                    scannedFiles = (mediaDuplicates + nonMedia).sortedByDescending { it.sizeBytes },
                    scanProgress = ScanProgressState(isScanning = false),
                    hasCompletedInitialScan = true,
                    cleanBanner = if (mediaDuplicates.isEmpty()) {
                        CleanSummaryBanner(0L, 0, "لم يتم العثور على صور مكررة في العناصر المختارة.")
                    } else {
                        CleanSummaryBanner(
                            0L,
                            0,
                            "تم اكتشاف ${mediaDuplicates.size} نسخة متطابقة في الصور المختارة."
                        )
                    }
                )
            }
        }
    }

    fun toggleIncludeOwnCache(include: Boolean) {
        internalState.update { it.copy(includeOwnCacheInClean = include) }
    }

    fun toggleFileSelection(itemId: String) {
        internalState.update { state ->
            state.copy(
                scannedFiles = state.scannedFiles.map { item ->
                    if (item.id == itemId) item.copy(isSelected = !item.isSelected) else item
                }
            )
        }
    }

    fun selectAllInCurrentFilter(selected: Boolean) {
        val currentFilter = internalState.value.selectedCategoryFilter
        internalState.update { state ->
            state.copy(
                scannedFiles = state.scannedFiles.map { item ->
                    if (item.isDeletableSaf && (currentFilter == null || item.category == currentFilter)) {
                        item.copy(isSelected = selected)
                    } else item
                }
            )
        }
    }

    fun setFileCategoryFilter(category: FileCategory?) {
        internalState.update { it.copy(selectedCategoryFilter = category) }
    }

    fun cleanSelectedItems() {
        if (internalState.value.cleaningProgress.isCleaning || internalState.value.scanProgress.isScanning) return
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val state = internalState.value
            val toDelete = state.scannedFiles.filter { it.isSelected && it.isDeletableSaf }

            internalState.update {
                it.copy(
                    cleaningProgress = CleaningProgressState(
                        isCleaning = true,
                        progress = 0.12f,
                        statusLabel = "جاري تجهيز الملفات والذاكرة المؤقتة..."
                    ),
                    cleanBanner = null
                )
            }
            delay(320L)

            internalState.update {
                it.copy(
                    cleaningProgress = CleaningProgressState(
                        isCleaning = true,
                        progress = 0.45f,
                        statusLabel = if (toDelete.isNotEmpty()) {
                            "جاري حذف ${toDelete.size} ملف محدد..."
                        } else {
                            "جاري تنظيف الذاكرة المؤقتة..."
                        }
                    )
                )
            }

            val (deletedFilesCount, filesBytesFreed) = if (toDelete.isNotEmpty()) {
                storageScanner.deleteSafDocuments(toDelete)
            } else {
                0 to 0L
            }
            delay(360L)

            internalState.update {
                it.copy(
                    cleaningProgress = CleaningProgressState(
                        isCleaning = true,
                        progress = 0.78f,
                        statusLabel = "جاري تحسين النظام وتحديث القياسات..."
                    )
                )
            }

            val cacheFreed = if (state.includeOwnCacheInClean) {
                appAnalyzer.clearOwnCache()
            } else 0L
            withContext(Dispatchers.Default) {
                System.gc()
            }
            delay(380L)

            internalState.update {
                it.copy(
                    cleaningProgress = CleaningProgressState(
                        isCleaning = true,
                        progress = 1.0f,
                        statusLabel = "اكتمل التنظيف!"
                    )
                )
            }
            delay(360L)

            val totalFreed = filesBytesFreed + cacheFreed
            val duration = (System.currentTimeMillis() - start).coerceAtLeast(120L)

            val categoriesUsed = buildList {
                if (cacheFreed > 0L) add("الذاكرة المؤقتة")
                if (deletedFilesCount > 0) add("ملفات المجلدات ($deletedFilesCount)")
            }.joinToString(" • ").ifEmpty { "تنظيف سريع" }

            if (totalFreed > 0L || deletedFilesCount > 0) {
                prefsRepo.recordCleanSession(
                    bytesFreed = totalFreed,
                    filesRemoved = deletedFilesCount,
                    categoriesSummary = categoriesUsed,
                    durationMillis = duration
                )
            }

            val deletedUriStrings = toDelete.map { it.uri.toString() }.toSet()
            val remainingCache = appAnalyzer.getOwnCacheBytes()
            val updatedTelemetry = telemetryMonitor.readSnapshot()

            internalState.update { current ->
                val updatedCategoryBytes = current.scannedCategoryBytes.toMutableMap()
                val updatedCategoryCounts = current.scannedCategoryCounts.toMutableMap()
                if (deletedFilesCount > 0) {
                    toDelete.forEach { removed ->
                        val cat = StorageScannerEngine.classifyFileCategory(removed.name, removed.mimeType)
                        updatedCategoryBytes[cat] = ((updatedCategoryBytes[cat] ?: 0L) - removed.sizeBytes).coerceAtLeast(0L)
                        updatedCategoryCounts[cat] = ((updatedCategoryCounts[cat] ?: 0) - 1).coerceAtLeast(0)
                    }
                }
                current.copy(
                    telemetry = updatedTelemetry,
                    ownCacheBytes = remainingCache,
                    scannedFiles = current.scannedFiles.filterNot {
                        it.isDeletableSaf && it.uri.toString() in deletedUriStrings
                    },
                    scannedCategoryBytes = updatedCategoryBytes,
                    scannedCategoryCounts = updatedCategoryCounts,
                    cleaningProgress = CleaningProgressState(isCleaning = false),
                    cleanBanner = CleanSummaryBanner(
                        bytesFreed = totalFreed,
                        filesRemoved = deletedFilesCount,
                        message = if (totalFreed > 0L || deletedFilesCount > 0) {
                            "تم التنظيف بنجاح!"
                        } else {
                            "تم الفحص والتنظيف السريع، جهازك في حالة ممتازة."
                        }
                    )
                )
            }
        }
    }

    fun setStorageChartMode(mode: StorageChartMode) {
        internalState.update { it.copy(storageChartMode = mode) }
    }

    fun runMemoryAndCacheOptimization() {
        boostSelectedMemory()
    }

    fun scanBackgroundProcesses() {
        viewModelScope.launch {
            val access = appAnalyzer.hasUsageAccess()
            internalState.update { it.copy(hasUsageAccess = access, isScanningProcesses = true) }
            val processes = memoryBooster.scanHeavyBackgroundProcesses()
            internalState.update {
                it.copy(
                    hasUsageAccess = access,
                    backgroundProcesses = processes,
                    isScanningProcesses = false
                )
            }
        }
    }

    fun toggleBackgroundProcessSelection(packageName: String) {
        internalState.update { state ->
            state.copy(
                backgroundProcesses = state.backgroundProcesses.map { proc ->
                    if (proc.packageName == packageName && !proc.isStopped) {
                        proc.copy(isSelected = !proc.isSelected)
                    } else proc
                }
            )
        }
    }

    fun selectAllBackgroundProcesses(selected: Boolean) {
        internalState.update { state ->
            state.copy(
                backgroundProcesses = state.backgroundProcesses.map { proc ->
                    if (!proc.isStopped) proc.copy(isSelected = selected) else proc
                }
            )
        }
    }

    fun boostSelectedMemory() {
        if (internalState.value.isBoostingMemory) return
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val selectedTargets = internalState.value.backgroundProcesses.filter { it.isSelected && !it.isStopped }
            val targets = if (selectedTargets.isNotEmpty()) {
                selectedTargets
            } else {
                internalState.value.backgroundProcesses.filter { !it.isStopped }
            }

            internalState.update { it.copy(isBoostingMemory = true) }
            val execution = memoryBooster.boostBackgroundMemory(targets)
            val duration = (System.currentTimeMillis() - start).coerceAtLeast(120L)

            val totalBytesReleased = (execution.ramFreedBytes + execution.cacheFreedBytes)
            if (totalBytesReleased > 0L || execution.backgroundKilledCount > 0) {
                prefsRepo.recordCleanSession(
                    bytesFreed = totalBytesReleased,
                    filesRemoved = execution.confirmedStoppedPackages.size,
                    categoriesSummary = "تعزيز الذاكرة RAM (${execution.backgroundKilledCount} عملية)",
                    durationMillis = duration
                )
            }

            val updatedTelemetry = telemetryMonitor.readSnapshot()
            val remainingCache = appAnalyzer.getOwnCacheBytes()
            val summaryMsg = when {
                execution.ramFreedBytes > 0L ->
                    "تم تحرير ${Formatters.formatBytes(execution.ramFreedBytes)} من RAM ومعالجة ${execution.backgroundKilledCount} عملية خلفية."
                execution.backgroundKilledCount > 0 ->
                    "تم إرسال إشارة إيقاف الخلفية لـ ${execution.backgroundKilledCount} تطبيق وتنظيف الكاش. استخدم «إيقاف إجباري» للتطبيقات العنيدة."
                else ->
                    "تم تحديث قراءات الذاكرة وتنظيف الكاش الداخلي بنجاح."
            }

            internalState.update { state ->
                val updatedList = state.backgroundProcesses.map { proc ->
                    if (proc.packageName in execution.confirmedStoppedPackages) {
                        proc.copy(isStopped = true, isSelected = false)
                    } else proc
                }
                state.copy(
                    telemetry = updatedTelemetry,
                    ownCacheBytes = remainingCache,
                    backgroundProcesses = updatedList,
                    isBoostingMemory = false,
                    memoryBoostResult = MemoryBoostResult(
                        ramFreedBytes = execution.ramFreedBytes,
                        backgroundKilledCount = execution.backgroundKilledCount,
                        cacheFreedBytes = execution.cacheFreedBytes,
                        summaryMessage = summaryMsg
                    ),
                    cleanBanner = CleanSummaryBanner(
                        bytesFreed = totalBytesReleased,
                        filesRemoved = execution.confirmedStoppedPackages.size,
                        message = summaryMsg
                    )
                )
            }
        }
    }

    fun attemptSingleBackgroundKill(packageName: String) {
        viewModelScope.launch {
            val target = internalState.value.backgroundProcesses.firstOrNull { it.packageName == packageName }
            if (target != null) {
                val execution = memoryBooster.boostBackgroundMemory(listOf(target))
                val updatedTelemetry = telemetryMonitor.readSnapshot()
                internalState.update { state ->
                    state.copy(
                        telemetry = updatedTelemetry,
                        backgroundProcesses = state.backgroundProcesses.map { proc ->
                            if (proc.packageName in execution.confirmedStoppedPackages) {
                                proc.copy(isStopped = true, isSelected = false)
                            } else proc
                        }
                    )
                }
            }
        }
    }

    fun startSelectedForceStopQueue() {
        val targets = internalState.value.backgroundProcesses.filter { it.isSelected && !it.isStopped }
        if (targets.isEmpty()) return
        val items = targets.map {
            ForceStopQueueItem(
                packageName = it.packageName,
                appName = it.appName,
                estimatedMemoryBytes = it.processMemoryBytes
            )
        }
        internalState.update {
            it.copy(
                forceStopQueue = ForceStopQueueState(
                    isActive = true,
                    items = items,
                    currentIndex = 0,
                    isComplete = false
                )
            )
        }
    }

    fun onForceStopAppReturned(packageName: String) {
        viewModelScope.launch {
            val isStoppedNow = withContext(Dispatchers.IO) {
                memoryBooster.isPackageStopped(packageName)
            }
            val updatedTelemetry = telemetryMonitor.readSnapshot()

            internalState.update { state ->
                val updatedProcesses = state.backgroundProcesses.map { proc ->
                    if (proc.packageName == packageName && isStoppedNow) {
                        proc.copy(isStopped = true, isSelected = false)
                    } else proc
                }

                val queue = state.forceStopQueue
                val updatedQueue = if (queue.isActive) {
                    val idx = queue.items.indexOfFirst {
                        it.packageName == packageName && it.status == ForceStopItemStatus.PENDING
                    }
                    if (idx >= 0) {
                        val mutable = queue.items.toMutableList()
                        mutable[idx] = mutable[idx].copy(
                            status = if (isStoppedNow) ForceStopItemStatus.STOPPED else ForceStopItemStatus.SKIPPED
                        )
                        val nextIdx = mutable.indexOfFirst { it.status == ForceStopItemStatus.PENDING }
                        val allDone = nextIdx < 0
                        ForceStopQueueState(
                            isActive = true,
                            items = mutable,
                            currentIndex = if (allDone) mutable.lastIndex.coerceAtLeast(0) else nextIdx,
                            isComplete = allDone
                        )
                    } else queue
                } else queue

                val bannerResult = if (isStoppedNow) {
                    val stoppedItem = state.backgroundProcesses.firstOrNull { it.packageName == packageName }
                    MemoryBoostResult(
                        ramFreedBytes = stoppedItem?.processMemoryBytes ?: 0L,
                        backgroundKilledCount = 1,
                        cacheFreedBytes = 0L,
                        summaryMessage = "تم الإيقاف الإجباري لـ ${stoppedItem?.appName ?: packageName} وتحرير ذاكرة الخلفية."
                    )
                } else state.memoryBoostResult

                state.copy(
                    telemetry = updatedTelemetry,
                    backgroundProcesses = updatedProcesses,
                    forceStopQueue = updatedQueue,
                    memoryBoostResult = bannerResult
                )
            }
        }
    }

    fun skipCurrentForceStopApp() {
        val queue = internalState.value.forceStopQueue
        if (!queue.isActive || queue.isComplete) return
        val idx = queue.currentIndex
        val currentItem = queue.items.getOrNull(idx) ?: return
        val updatedItems = queue.items.toMutableList()
        updatedItems[idx] = currentItem.copy(status = ForceStopItemStatus.SKIPPED)
        val nextIdx = updatedItems.indexOfFirst { it.status == ForceStopItemStatus.PENDING }
        val allDone = nextIdx < 0
        internalState.update {
            it.copy(
                forceStopQueue = ForceStopQueueState(
                    isActive = true,
                    items = updatedItems,
                    currentIndex = if (allDone) updatedItems.lastIndex.coerceAtLeast(0) else nextIdx,
                    isComplete = allDone
                )
            )
        }
    }

    fun dismissForceStopQueue() {
        internalState.update {
            it.copy(forceStopQueue = ForceStopQueueState(isActive = false))
        }
        scanBackgroundProcesses()
    }

    fun dismissMemoryBoostResult() {
        internalState.update { it.copy(memoryBoostResult = null) }
    }

    fun setAppSortFilter(filter: AppSortFilter) {
        internalState.update { it.copy(appSortFilter = filter) }
    }

    fun setAppSearchQuery(query: String) {
        internalState.update { it.copy(appSearchQuery = query) }
    }

    fun toggleAppSelection(packageName: String) {
        internalState.update { state ->
            val updated = if (packageName in state.selectedAppPackages) {
                state.selectedAppPackages - packageName
            } else {
                state.selectedAppPackages + packageName
            }
            state.copy(selectedAppPackages = updated)
        }
    }

    fun selectAllFilteredApps(selected: Boolean) {
        internalState.update { state ->
            val filteredPkgs = state.filteredApps.map { it.packageName }.toSet()
            val updated = if (selected) {
                state.selectedAppPackages + filteredPkgs
            } else {
                state.selectedAppPackages - filteredPkgs
            }
            state.copy(selectedAppPackages = updated)
        }
    }

    fun clearSingleAppCache(packageName: String) {
        startAppCacheClearQueue(setOf(packageName))
    }

    fun startSelectedAppCacheClearing() {
        val selected = internalState.value.selectedAppPackages
        if (selected.isEmpty()) return
        startAppCacheClearQueue(selected)
    }

    private fun startAppCacheClearQueue(targetPackages: Set<String>) {
        viewModelScope.launch {
            val state = internalState.value
            val ownPkg = appAnalyzer.ownPackageName
            val orderedTargets = state.filteredApps.filter { it.packageName in targetPackages }
                .ifEmpty { state.installedApps.filter { it.packageName in targetPackages } }
            if (orderedTargets.isEmpty()) return@launch

            val queueItems = orderedTargets.map { app ->
                AppCacheClearItem(
                    packageName = app.packageName,
                    appName = app.appName,
                    initialCacheBytes = app.cacheBytes,
                    remainingCacheBytes = app.cacheBytes,
                    status = AppCacheItemStatus.PENDING,
                    isOwnApp = app.packageName == ownPkg
                )
            }.toMutableList()

            // Clear own app cache immediately if included
            val ownIdx = queueItems.indexOfFirst { it.isOwnApp }
            var ownFreed = 0L
            var newOwnCache = state.ownCacheBytes
            if (ownIdx >= 0) {
                ownFreed = appAnalyzer.clearOwnCache()
                newOwnCache = appAnalyzer.getOwnCacheBytes()
                val item = queueItems[ownIdx]
                queueItems[ownIdx] = item.copy(
                    remainingCacheBytes = newOwnCache,
                    status = AppCacheItemStatus.CLEARED
                )
                if (ownFreed > 0L) {
                    prefsRepo.recordCleanSession(
                        bytesFreed = ownFreed,
                        filesRemoved = 0,
                        categoriesSummary = "كاش ${item.appName}",
                        durationMillis = 120L
                    )
                }
            }

            val nextPendingIdx = queueItems.indexOfFirst { it.status == AppCacheItemStatus.PENDING }
            val allDone = nextPendingIdx < 0
            val updatedApps = if (ownIdx >= 0) {
                state.installedApps.map { app ->
                    if (app.packageName == ownPkg) app.copy(cacheBytes = newOwnCache) else app
                }
            } else {
                state.installedApps
            }

            internalState.update { current ->
                current.copy(
                    ownCacheBytes = newOwnCache,
                    installedApps = updatedApps,
                    selectedAppPackages = if (allDone) current.selectedAppPackages - targetPackages else current.selectedAppPackages,
                    appCacheClearQueue = AppCacheClearQueueState(
                        isActive = true,
                        items = queueItems,
                        currentIndex = if (allDone) 0 else nextPendingIdx,
                        isComplete = allDone
                    )
                )
            }
        }
    }

    fun onAppSettingsReturned(packageName: String) {
        viewModelScope.launch {
            val queue = internalState.value.appCacheClearQueue
            if (!queue.isActive) return@launch
            val idx = queue.items.indexOfFirst {
                it.packageName == packageName && it.status == AppCacheItemStatus.PENDING
            }
            if (idx < 0) return@launch

            val updatedCache = appAnalyzer.queryPackageCacheBytes(packageName)
            val currentItems = queue.items.toMutableList()
            val item = currentItems[idx]
            val remaining = updatedCache ?: 0L
            val freed = (item.initialCacheBytes - remaining).coerceAtLeast(0L)

            currentItems[idx] = item.copy(
                remainingCacheBytes = remaining,
                status = AppCacheItemStatus.CLEARED
            )

            if (freed > 0L) {
                prefsRepo.recordCleanSession(
                    bytesFreed = freed,
                    filesRemoved = 0,
                    categoriesSummary = "كاش ${item.appName}",
                    durationMillis = 150L
                )
            }

            val nextPendingIdx = currentItems.indexOfFirst { it.status == AppCacheItemStatus.PENDING }
            val allDone = nextPendingIdx < 0
            val updatedTelemetry = telemetryMonitor.readSnapshot()

            internalState.update { state ->
                val updatedApps = if (updatedCache != null) {
                    state.installedApps.map { app ->
                        if (app.packageName == packageName) app.copy(cacheBytes = updatedCache) else app
                    }
                } else {
                    state.installedApps
                }
                state.copy(
                    telemetry = updatedTelemetry,
                    installedApps = updatedApps,
                    selectedAppPackages = if (allDone) state.selectedAppPackages - item.packageName else state.selectedAppPackages - packageName,
                    appCacheClearQueue = AppCacheClearQueueState(
                        isActive = true,
                        items = currentItems,
                        currentIndex = if (allDone) currentItems.lastIndex.coerceAtLeast(0) else nextPendingIdx,
                        isComplete = allDone
                    )
                )
            }
        }
    }

    fun skipCurrentAppInQueue() {
        val queue = internalState.value.appCacheClearQueue
        if (!queue.isActive || queue.isComplete) return
        val currentIdx = queue.currentIndex
        val currentItem = queue.items.getOrNull(currentIdx) ?: return
        val updatedItems = queue.items.toMutableList()
        updatedItems[currentIdx] = currentItem.copy(status = AppCacheItemStatus.SKIPPED)
        val nextPendingIdx = updatedItems.indexOfFirst { it.status == AppCacheItemStatus.PENDING }
        val allDone = nextPendingIdx < 0

        internalState.update { state ->
            state.copy(
                selectedAppPackages = state.selectedAppPackages - currentItem.packageName,
                appCacheClearQueue = AppCacheClearQueueState(
                    isActive = true,
                    items = updatedItems,
                    currentIndex = if (allDone) updatedItems.lastIndex.coerceAtLeast(0) else nextPendingIdx,
                    isComplete = allDone
                )
            )
        }
    }

    fun dismissAppCacheClearQueue() {
        internalState.update { state ->
            state.copy(
                appCacheClearQueue = AppCacheClearQueueState(isActive = false)
            )
        }
        refreshUsageAccessAndApps()
    }

    fun startSelectedAppsUninstall() {
        val state = internalState.value
        val ownPkg = appAnalyzer.ownPackageName
        val targets = state.filteredApps.filter {
            it.packageName in state.selectedAppPackages && !it.isSystemApp && it.packageName != ownPkg
        }
        if (targets.isEmpty()) return

        val queueItems = targets.map {
            AppUninstallItem(
                packageName = it.packageName,
                appName = it.appName,
                sizeBytes = it.totalBytes
            )
        }
        internalState.update {
            it.copy(
                appUninstallQueue = AppUninstallQueueState(
                    isActive = true,
                    items = queueItems,
                    currentIndex = 0,
                    isComplete = false
                )
            )
        }
    }

    fun onAppUninstallReturned(packageName: String) {
        viewModelScope.launch {
            val queue = internalState.value.appUninstallQueue
            if (!queue.isActive) return@launch
            val idx = queue.items.indexOfFirst {
                it.packageName == packageName && it.status == AppUninstallStatus.PENDING
            }
            if (idx < 0) return@launch

            val stillInstalled = withContext(Dispatchers.IO) {
                appAnalyzer.isPackageInstalled(packageName)
            }
            val updatedItems = queue.items.toMutableList()
            val item = updatedItems[idx]
            updatedItems[idx] = item.copy(
                status = if (!stillInstalled) AppUninstallStatus.UNINSTALLED else AppUninstallStatus.SKIPPED
            )
            if (!stillInstalled && item.sizeBytes > 0L) {
                prefsRepo.recordCleanSession(
                    bytesFreed = item.sizeBytes,
                    filesRemoved = 1,
                    categoriesSummary = "إلغاء تثبيت ${item.appName}",
                    durationMillis = 120L
                )
            }

            val nextIdx = updatedItems.indexOfFirst { it.status == AppUninstallStatus.PENDING }
            val allDone = nextIdx < 0
            internalState.update { state ->
                state.copy(
                    installedApps = if (!stillInstalled) {
                        state.installedApps.filterNot { it.packageName == packageName }
                    } else {
                        state.installedApps
                    },
                    selectedAppPackages = state.selectedAppPackages - packageName,
                    appUninstallQueue = AppUninstallQueueState(
                        isActive = true,
                        items = updatedItems,
                        currentIndex = if (allDone) updatedItems.lastIndex.coerceAtLeast(0) else nextIdx,
                        isComplete = allDone
                    )
                )
            }
        }
    }

    fun skipCurrentUninstallApp() {
        val queue = internalState.value.appUninstallQueue
        if (!queue.isActive || queue.isComplete) return
        val idx = queue.currentIndex
        val item = queue.items.getOrNull(idx) ?: return
        val updatedItems = queue.items.toMutableList()
        updatedItems[idx] = item.copy(status = AppUninstallStatus.SKIPPED)
        val nextIdx = updatedItems.indexOfFirst { it.status == AppUninstallStatus.PENDING }
        val allDone = nextIdx < 0
        internalState.update { state ->
            state.copy(
                selectedAppPackages = state.selectedAppPackages - item.packageName,
                appUninstallQueue = AppUninstallQueueState(
                    isActive = true,
                    items = updatedItems,
                    currentIndex = if (allDone) updatedItems.lastIndex.coerceAtLeast(0) else nextIdx,
                    isComplete = allDone
                )
            )
        }
    }

    fun dismissAppUninstallQueue() {
        internalState.update {
            it.copy(appUninstallQueue = AppUninstallQueueState(isActive = false))
        }
        refreshUsageAccessAndApps()
    }

    fun refreshClipboardStatus() {
        val status = ClipboardCleanerHelper.readStatus(appContext)
        internalState.update { it.copy(clipboardStatus = status) }
    }

    fun clearClipboard() {
        val cleared = ClipboardCleanerHelper.clearClipboard(appContext)
        val status = ClipboardCleanerHelper.readStatus(appContext)
        internalState.update {
            it.copy(
                clipboardStatus = status,
                cleanBanner = if (cleared) {
                    CleanSummaryBanner(
                        bytesFreed = 0L,
                        filesRemoved = 1,
                        message = "تم مسح محتوى الحافظة لحماية خصوصيتك."
                    )
                } else it.cleanBanner
            )
        }
    }

    fun analyzePickedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            internalState.update {
                it.copy(isAnalyzingPhotos = true, photoOptimizeBanner = null)
            }
            val preset = internalState.value.photoCompressionPreset
            val analyzed = photoOptimizer.analyzePhotos(uris, preset)
            internalState.update {
                it.copy(
                    analyzedPhotos = analyzed,
                    isAnalyzingPhotos = false
                )
            }
        }
    }

    fun setPhotoCompressionPreset(preset: PhotoCompressionPreset) {
        viewModelScope.launch {
            internalState.update { it.copy(photoCompressionPreset = preset) }
            val current = internalState.value.analyzedPhotos
            if (current.isNotEmpty()) {
                val updated = photoOptimizer.reestimateWithPreset(current, preset)
                internalState.update { it.copy(analyzedPhotos = updated) }
            }
        }
    }

    fun togglePhotoSelection(uri: Uri) {
        internalState.update { state ->
            state.copy(
                analyzedPhotos = state.analyzedPhotos.map { item ->
                    if (item.uri == uri) item.copy(isSelected = !item.isSelected) else item
                }
            )
        }
    }

    fun selectAllPhotos(selected: Boolean) {
        internalState.update { state ->
            state.copy(
                analyzedPhotos = state.analyzedPhotos.map { it.copy(isSelected = selected) }
            )
        }
    }

    fun compressSelectedPhotos() {
        val selected = internalState.value.analyzedPhotos.filter { it.isSelected }
        if (selected.isEmpty() || internalState.value.isCompressingPhotos) return
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            internalState.update { it.copy(isCompressingPhotos = true, photoOptimizeBanner = null) }
            val result = photoOptimizer.compressAndSavePhotos(selected, internalState.value.photoCompressionPreset)
            if (result.savedBytes > 0L) {
                prefsRepo.recordCleanSession(
                    bytesFreed = result.savedBytes,
                    filesRemoved = result.processedCount,
                    categoriesSummary = "ضغط وتحسين الصور (${result.processedCount})",
                    durationMillis = (System.currentTimeMillis() - start).coerceAtLeast(150L)
                )
            }
            internalState.update {
                it.copy(
                    isCompressingPhotos = false,
                    photoOptimizeBanner = "تم ضغط وحفظ ${result.processedCount} صورة في مجلد Pictures/CleanPro • التوفير: ${Formatters.formatBytes(result.savedBytes)}"
                )
            }
        }
    }

    fun clearPhotoAnalysis() {
        internalState.update { it.copy(analyzedPhotos = emptyList(), photoOptimizeBanner = null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setLargeFileThresholdMb(mb: Int) {
        viewModelScope.launch { prefsRepo.setLargeFileThresholdMb(mb) }
    }

    fun setAutoCleanSchedule(
        enabled: Boolean,
        frequency: CleaningScheduleFrequency,
        onlyWhenCharging: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.setAutoCleanSchedule(enabled, frequency, onlyWhenCharging)
            val batterySaver = prefsRepo.preferencesFlow.first().batterySaverEnabled
            if (enabled && !batterySaver) {
                ReminderScheduler.scheduleAutoClean(appContext, frequency, onlyWhenCharging)
            } else {
                ReminderScheduler.cancelAutoClean(appContext)
            }
        }
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setBatterySaverEnabled(enabled)
            val prefs = prefsRepo.preferencesFlow.first()
            if (enabled) {
                // Reduce background activity by pausing periodic background cleaning jobs
                ReminderScheduler.cancelAutoClean(appContext)
            } else if (prefs.autoCleanScheduleEnabled) {
                ReminderScheduler.scheduleAutoClean(
                    appContext,
                    prefs.autoCleanFrequency,
                    prefs.autoCleanOnlyWhenCharging
                )
            }
        }
    }

    fun setQuickCleanNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setQuickCleanNotificationEnabled(enabled)
            if (enabled) {
                QuickCleanNotificationManager.showOrUpdate(appContext)
            } else {
                QuickCleanNotificationManager.cancel(appContext)
            }
        }
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setLiquidGlassEnabled(enabled)
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setCloudSyncEnabled(enabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { prefsRepo.clearHistory() }
    }

    fun dismissBanner() {
        internalState.update { it.copy(cleanBanner = null) }
    }
}
