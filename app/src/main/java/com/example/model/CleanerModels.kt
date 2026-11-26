package com.example.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import java.util.Locale

data class DeviceTelemetry(
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val availRamBytes: Long = 0L,
    val usedRamBytes: Long = 0L,
    val isLowMemory: Boolean = false,
    val ramLowThresholdBytes: Long = 0L,
    val batteryPercent: Int = 0,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val isCharging: Boolean = false,
    val batteryHealthLabel: String = "جيدة",
    val batteryChargeStatusLabel: String = "قيد التفريغ",
    val batteryPluggedSourceLabel: String = "طاقة البطارية",
    val batteryTechnology: String = "Li-ion",
    val batteryCurrentMa: Int? = null,
    val batteryEstimatedCapacityMah: Int? = null,
    val batteryHealthScore: Int = 100,
    val batteryThermalStatusLabel: String = "مثالية",
    val batteryTimeRemainingMillis: Long = 0L,
    val isChargeTimeEstimate: Boolean = false,
    val isHardwareChargeEstimate: Boolean = false,
    val deviceModel: String = "",
    val androidVersion: String = "",
    val cpuCores: Int = 1,
    val cpuAbi: String = "",
    val cpuTempCelsius: Float? = null,
    val cpuTempSourceLabel: String = "حرارة البطارية / الهيكل",
    val thermalStatusLabel: String = "طبيعية",
    val thermalSeverityLevel: Int = 0,
    val thermalHeadroom: Float? = null,
    val cpuCurrentFreqMhz: Int? = null,
    val cpuMaxFreqMhz: Int? = null,
    val uptimeMillis: Long = 0L,
    val storageBlockSizeBytes: Long = 4096L,
    val storageTotalBlocks: Long = 0L,
    val storageAvailableBlocks: Long = 0L,
    val appCacheQuotaBytes: Long = 0L
) {
    val storageUsageFraction: Float
        get() = if (totalStorageBytes > 0L) {
            (usedStorageBytes.toDouble() / totalStorageBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f

    val ramUsageFraction: Float
        get() = if (totalRamBytes > 0L) {
            (usedRamBytes.toDouble() / totalRamBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f

    val effectiveCpuTempCelsius: Float
        get() = cpuTempCelsius ?: batteryTempCelsius

    val batteryFraction: Float
        get() = (batteryPercent.toFloat() / 100f).coerceIn(0f, 1f)
}

data class BatteryHealthEstimate(
    val healthScore: Int,
    val thermalStatusLabel: String,
    val estimatedCapacityMah: Int?,
    val remainingTimeMillis: Long,
    val isChargeTime: Boolean,
    val isHardwareMeasured: Boolean
)

enum class StorageHealthStatus(val labelAr: String) {
    HEALTHY("صحة ممتازة"),
    ATTENTION("تحتاج لتحرير مساحة"),
    CRITICAL("حالة حرجة — مساحة منخفضة جداً")
}

data class StorageHealthDiagnostics(
    val status: StorageHealthStatus = StorageHealthStatus.HEALTHY,
    val healthScore: Int = 100,
    val freeSpacePercent: Float = 100f,
    val writeSpeedMbPerSec: Float? = null,
    val readSpeedMbPerSec: Float? = null,
    val ioLatencyMs: Long? = null,
    val blockSizeBytes: Long = 4096L,
    val isFragmentationRiskHigh: Boolean = false,
    val cachePressureRatio: Float = 0f,
    val recommendationAr: String = "المساحة الحرة كافية لأداء قراءة وكتابة مستقر.",
    val lastCheckedMillis: Long = 0L
)

enum class FileCategory(val labelAr: String) {
    TEMP_AND_EMPTY("ملفات مؤقتة وفارغة"),
    RESIDUAL_APK("حزم APK متبقية"),
    LARGE_FILES("ملفات كبيرة الحجم"),
    DUPLICATE_FILES("ملفات مكررة")
}

data class ScannedFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val folderName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val category: FileCategory,
    val duplicateGroupHash: String? = null,
    val isSelected: Boolean = false,
    val isDeletableSaf: Boolean = true
)

data class SafFolderInfo(
    val uriString: String,
    val displayName: String,
    val hasValidPermission: Boolean
)

enum class AppSortFilter(val labelAr: String) {
    MOST_CACHE("أكبر ذاكرة مؤقتة"),
    LARGEST_SIZE("الأكبر حجماً"),
    LEAST_USED("نادر الاستخدام"),
    ALL_AZ("الكل (أبجدي)")
}

enum class AppImpactLevel(val labelAr: String) {
    HIGH("تأثير مرتفع"),
    MEDIUM("تأثير متوسط"),
    LOW("تأثير خفيف")
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val lastTimeUsedMillis: Long,
    val totalForegroundMillis: Long,
    val isSystemApp: Boolean,
    val hasDetailedStats: Boolean
) {
    val totalBytes: Long
        get() = appBytes + dataBytes + cacheBytes

    val impactLevel: AppImpactLevel
        get() = when {
            totalBytes >= 200L * 1024L * 1024L ||
                cacheBytes >= 50L * 1024L * 1024L ||
                totalForegroundMillis >= 4L * 60L * 60L * 1000L -> AppImpactLevel.HIGH
            totalBytes >= 60L * 1024L * 1024L ||
                cacheBytes >= 15L * 1024L * 1024L ||
                totalForegroundMillis >= 30L * 60L * 1000L -> AppImpactLevel.MEDIUM
            else -> AppImpactLevel.LOW
        }
}

enum class AppCacheItemStatus {
    PENDING,
    CLEARED,
    SKIPPED
}

data class AppCacheClearItem(
    val packageName: String,
    val appName: String,
    val initialCacheBytes: Long,
    val remainingCacheBytes: Long = initialCacheBytes,
    val status: AppCacheItemStatus = AppCacheItemStatus.PENDING,
    val isOwnApp: Boolean = false
) {
    val freedBytes: Long
        get() = (initialCacheBytes - remainingCacheBytes).coerceAtLeast(0L)
}

data class AppCacheClearQueueState(
    val isActive: Boolean = false,
    val items: List<AppCacheClearItem> = emptyList(),
    val currentIndex: Int = 0,
    val isComplete: Boolean = false
) {
    val currentItem: AppCacheClearItem?
        get() = items.getOrNull(currentIndex)

    val processedCount: Int
        get() = items.count { it.status != AppCacheItemStatus.PENDING }

    val totalFreedBytes: Long
        get() = items.sumOf { it.freedBytes }
}

data class ScanProgressState(
    val isScanning: Boolean = false,
    val currentStepLabel: String = "",
    val filesScannedCount: Int = 0
)

data class CleaningProgressState(
    val isCleaning: Boolean = false,
    val progress: Float = 0f,
    val statusLabel: String = ""
)

enum class CleaningScheduleFrequency(val intervalHours: Int, val labelAr: String, val detailAr: String) {
    DAILY(24, "يومي", "كل 24 ساعة"),
    WEEKLY(168, "أسبوعي", "كل 7 أيام")
}

data class CleanSummaryBanner(
    val bytesFreed: Long,
    val filesRemoved: Int,
    val message: String
)

enum class DiskStorageCategory(val labelAr: String) {
    PHOTOS("الصور"),
    VIDEOS("الفيديو"),
    AUDIO("الصوتيات"),
    DOCUMENTS("المستندات"),
    ARCHIVES_APK("APK والأرشيف"),
    APPS("التطبيقات"),
    OTHER("أخرى والنظام"),
    FREE("مساحة متاحة")
}

data class DiskCategoryUsage(
    val category: DiskStorageCategory,
    val bytes: Long,
    val itemCount: Int = 0
)

data class StorageBreakdownResult(
    val scannedItems: List<ScannedFileItem> = emptyList(),
    val categoryBytes: Map<DiskStorageCategory, Long> = emptyMap(),
    val categoryCounts: Map<DiskStorageCategory, Int> = emptyMap()
)

enum class StorageChartMode(val labelAr: String) {
    FULL_DISK("إجمالي القرص"),
    FILE_CATEGORIES("تصنيفات الملفات")
}

enum class AppUninstallStatus {
    PENDING,
    UNINSTALLED,
    SKIPPED
}

data class AppUninstallItem(
    val packageName: String,
    val appName: String,
    val sizeBytes: Long,
    val status: AppUninstallStatus = AppUninstallStatus.PENDING
)

data class AppUninstallQueueState(
    val isActive: Boolean = false,
    val items: List<AppUninstallItem> = emptyList(),
    val currentIndex: Int = 0,
    val isComplete: Boolean = false
) {
    val currentItem: AppUninstallItem?
        get() = items.getOrNull(currentIndex)

    val processedCount: Int
        get() = items.count { it.status != AppUninstallStatus.PENDING }

    val uninstalledCount: Int
        get() = items.count { it.status == AppUninstallStatus.UNINSTALLED }

    val estimatedFreedBytes: Long
        get() = items.filter { it.status == AppUninstallStatus.UNINSTALLED }.sumOf { it.sizeBytes }
}

data class ClipboardStatus(
    val hasContent: Boolean = false,
    val itemCount: Int = 0,
    val previewSummary: String = "الحافظة فارغة",
    val characterCount: Int = 0
)

enum class PhotoQualityIssue(val labelAr: String) {
    NONE("جودة جيدة"),
    BLURRY("مهزوزة / غير واضحة"),
    UNDEREXPOSED("إضاءة منخفضة"),
    OVEREXPOSED("إضاءة زائدة"),
    DUPLICATE("نسخة مكررة")
}

enum class PhotoCompressionPreset(
    val quality: Int,
    val maxDimensionPx: Int,
    val labelAr: String,
    val detailAr: String
) {
    LIGHT(85, 2560, "ضغط خفيف", "جودة عالية 85%"),
    BALANCED(70, 1920, "متوازن", "توفير متوازن 70%"),
    HIGH_SAVING(50, 1280, "توفير أقصى", "أصغر حجم 50%")
}

data class AnalyzedPhotoItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val sharpnessScore: Double,
    val meanLuminance: Double,
    val issue: PhotoQualityIssue,
    val duplicateGroupHash: String? = null,
    val estimatedCompressedBytes: Long = sizeBytes,
    val thumbnail: Bitmap? = null,
    val isSelected: Boolean = true
) {
    val estimatedSavedBytes: Long
        get() = (sizeBytes - estimatedCompressedBytes).coerceAtLeast(0L)
}

enum class ProcessResourceTier(val labelAr: String) {
    CRITICAL("استهلاك مرتفع جداً"),
    HIGH("استهلاك مرتفع"),
    MODERATE("استهلاك متوسط")
}

data class BackgroundProcessInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val pid: Int? = null,
    val processMemoryBytes: Long,
    val isLivePssMeasured: Boolean = false,
    val lastTimeUsedMillis: Long,
    val totalForegroundMillis: Long,
    val cacheBytes: Long,
    val importanceLabelAr: String,
    val resourceScore: Int,
    val resourceTier: ProcessResourceTier,
    val isStopped: Boolean = false,
    val isSelected: Boolean = true
)

enum class ForceStopItemStatus {
    PENDING,
    STOPPED,
    SKIPPED
}

data class ForceStopQueueItem(
    val packageName: String,
    val appName: String,
    val estimatedMemoryBytes: Long,
    val status: ForceStopItemStatus = ForceStopItemStatus.PENDING
)

data class ForceStopQueueState(
    val isActive: Boolean = false,
    val items: List<ForceStopQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val isComplete: Boolean = false
) {
    val currentItem: ForceStopQueueItem?
        get() = items.getOrNull(currentIndex)

    val processedCount: Int
        get() = items.count { it.status != ForceStopItemStatus.PENDING }

    val stoppedCount: Int
        get() = items.count { it.status == ForceStopItemStatus.STOPPED }

    val estimatedFreedRamBytes: Long
        get() = items.filter { it.status == ForceStopItemStatus.STOPPED }.sumOf { it.estimatedMemoryBytes }
}

data class MemoryBoostResult(
    val ramFreedBytes: Long,
    val backgroundKilledCount: Int,
    val cacheFreedBytes: Long,
    val timestampMillis: Long = System.currentTimeMillis(),
    val summaryMessage: String
)

object Formatters {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatPercent(fraction: Float): String {
        return String.format(Locale.US, "%.0f%%", (fraction * 100f).coerceIn(0f, 100f))
    }

    fun formatDuration(durationMillis: Long): String {
        if (durationMillis <= 0L) return "0د"
        val totalSeconds = durationMillis / 1000L
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return when {
            days > 0L -> "${days}ي ${hours}س ${minutes}د"
            hours > 0L -> "${hours}س ${minutes}د"
            else -> "${minutes}د"
        }
    }

    fun formatRemainingTime(durationMillis: Long, isCharging: Boolean, batteryPercent: Int): String {
        if (isCharging && batteryPercent >= 100) return "مكتمل الشحن (100%)"
        if (durationMillis <= 0L) {
            return if (isCharging) "جاري حساب وقت الامتلاء…" else "جاري حساب الوقت المتبقي…"
        }
        val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(1L)
        val days = totalMinutes / 1440L
        val hours = (totalMinutes % 1440L) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> "${days}ي ${hours}س ${minutes}د"
            hours > 0L && minutes > 0L -> "${hours}س ${minutes}د"
            hours > 0L -> "${hours} ساعة"
            else -> "${minutes} دقيقة"
        }
    }
}
