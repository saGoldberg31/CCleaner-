package com.example.engine

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.example.model.BackgroundProcessInfo
import com.example.model.ProcessResourceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class MemoryBoostExecution(
    val ramFreedBytes: Long,
    val cacheFreedBytes: Long,
    val backgroundKilledCount: Int,
    val confirmedStoppedPackages: Set<String>
)

class MemoryBoosterEngine(
    private val context: Context,
    private val appAnalyzer: AppAnalyzerEngine
) {
    private val protectedSystemPrefixes = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.launcher"
    )

    suspend fun scanHeavyBackgroundProcesses(): List<BackgroundProcessInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val usageAccess = appAnalyzer.hasUsageAccess()
        val now = System.currentTimeMillis()

        // 1. Live running processes (visible PIDs & PSS RAM)
        val runningByPkg = mutableMapOf<String, Pair<ActivityManager.RunningAppProcessInfo, Long>>()
        runCatching {
            val procs = activityManager?.runningAppProcesses.orEmpty()
            for (proc in procs) {
                val pssBytes = runCatching {
                    val memInfos = activityManager?.getProcessMemoryInfo(intArrayOf(proc.pid))
                    val pssKb = memInfos?.firstOrNull()?.totalPss ?: 0
                    pssKb.toLong().coerceAtLeast(0L) * 1024L
                }.getOrDefault(0L)
                for (pkg in proc.pkgList.orEmpty()) {
                    if (pkg != context.packageName) {
                        val existing = runningByPkg[pkg]
                        if (existing == null || pssBytes > existing.second) {
                            runningByPkg[pkg] = proc to pssBytes
                        }
                    }
                }
            }
        }

        // 2. Recent activity & foreground services via UsageStats (when Usage Access is granted)
        val usageByPkg = mutableMapOf<String, UsageStats>()
        val recentEventPkgs = mutableSetOf<String>()
        val activeServicePkgs = mutableSetOf<String>()

        if (usageAccess) {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageManager != null) {
                val statsStart = now - 24L * 60L * 60L * 1000L
                runCatching {
                    usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, statsStart, now)
                        ?.forEach { stat ->
                            val existing = usageByPkg[stat.packageName]
                            if (existing == null || stat.lastTimeUsed > existing.lastTimeUsed) {
                                usageByPkg[stat.packageName] = stat
                            }
                        }
                }

                val eventStart = now - 6L * 60L * 60L * 1000L
                runCatching {
                    val events = usageManager.queryEvents(eventStart, now)
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.packageName == context.packageName) continue
                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.ACTIVITY_PAUSED -> recentEventPkgs.add(event.packageName)
                            UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                                recentEventPkgs.add(event.packageName)
                                activeServicePkgs.add(event.packageName)
                            }
                        }
                    }
                }
            }
        }

        val storageStatsManager = if (usageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null
        val userHandle = Process.myUserHandle()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seen = HashSet<String>()
        val candidates = mutableListOf<BackgroundProcessInfo>()

        for (resolve in resolveInfos) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            if (pkg == context.packageName) continue
            if (protectedSystemPrefixes.any { pkg == it || pkg.startsWith("$it.") }) continue

            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull() ?: continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val runningEntry = runningByPkg[pkg]

            // Skip system apps unless directly present in runningAppProcesses
            if (isSystem && runningEntry == null) continue

            // Android marks force-stopped or never-launched apps with FLAG_STOPPED
            val isSystemStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            if (isSystemStopped && runningEntry == null) continue

            val usage = usageByPkg[pkg]
            val lastUsed = usage?.lastTimeUsed ?: 0L
            val fgMillis = usage?.totalTimeInForeground ?: 0L
            val isRecentlyActive = (lastUsed > 0L && (now - lastUsed) <= 24L * 60L * 60L * 1000L) ||
                pkg in recentEventPkgs

            // If Usage Access is granted, focus on processes with live PID OR recent activity / active service
            if (usageAccess && runningEntry == null && !isRecentlyActive) {
                continue
            }

            var appBytes = 0L
            var dataBytes = 0L
            var cacheBytes = 0L
            if (storageStatsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val stats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkg,
                        userHandle
                    )
                    appBytes = stats.appBytes
                    dataBytes = stats.dataBytes
                    cacheBytes = stats.cacheBytes
                }
            }
            if (appBytes == 0L) {
                appBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L)
            }

            val totalFootprintBytes = appBytes + dataBytes + cacheBytes
            val livePssBytes = runningEntry?.second ?: 0L
            val isLivePss = livePssBytes > 0L

            val estimatedMemoryBytes = if (isLivePss) {
                livePssBytes
            } else {
                val activityBoost = if (pkg in activeServicePkgs) 64L * 1024L * 1024L else 0L
                val footprintFraction = ((appBytes + dataBytes) * 0.22 + cacheBytes * 0.45).roundToInt().toLong()
                (footprintFraction + activityBoost).coerceIn(28L * 1024L * 1024L, 512L * 1024L * 1024L)
            }

            val (score, tier) = computeProcessScoreAndTier(
                livePssBytes = livePssBytes,
                totalStorageBytes = totalFootprintBytes,
                totalForegroundMillis = fgMillis,
                lastTimeUsedMillis = lastUsed,
                nowMillis = now,
                hasActiveService = pkg in activeServicePkgs
            )

            // Only include apps with noticeable resource footprint or active running process
            if (runningEntry == null && score < 30 && totalFootprintBytes < 45L * 1024L * 1024L) {
                continue
            }

            val importanceLabel = when {
                runningEntry != null -> importanceToLabel(runningEntry.first.importance)
                pkg in activeServicePkgs -> "خدمة خلفية نشطة"
                lastUsed > 0L && (now - lastUsed) <= 60L * 60L * 1000L -> "نشط في الخلفية مؤخراً"
                else -> "محمل في الذاكرة"
            }

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()

            candidates.add(
                BackgroundProcessInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    pid = runningEntry?.first?.pid,
                    processMemoryBytes = estimatedMemoryBytes,
                    isLivePssMeasured = isLivePss,
                    lastTimeUsedMillis = lastUsed,
                    totalForegroundMillis = fgMillis,
                    cacheBytes = cacheBytes,
                    importanceLabelAr = importanceLabel,
                    resourceScore = score,
                    resourceTier = tier,
                    isStopped = isSystemStopped,
                    isSelected = tier != ProcessResourceTier.MODERATE || runningEntry != null
                )
            )
        }

        candidates.sortedWith(
            compareByDescending<BackgroundProcessInfo> { it.resourceScore }
                .thenByDescending { it.processMemoryBytes }
        )
    }

    suspend fun boostBackgroundMemory(targets: List<BackgroundProcessInfo>): MemoryBoostExecution =
        withContext(Dispatchers.IO) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memBefore = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memBefore)

            var killRequests = 0
            val confirmedStopped = mutableSetOf<String>()

            for (target in targets) {
                if (target.packageName == context.packageName) continue
                runCatching {
                    activityManager?.killBackgroundProcesses(target.packageName)
                    killRequests++
                }
                if (isPackageStopped(target.packageName)) {
                    confirmedStopped.add(target.packageName)
                }
            }

            val cacheFreed = appAnalyzer.clearOwnCache()
            System.gc()

            val memAfter = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memAfter)
            val rawRamDelta = (memAfter.availMem - memBefore.availMem).coerceAtLeast(0L)

            MemoryBoostExecution(
                ramFreedBytes = rawRamDelta,
                cacheFreedBytes = cacheFreed,
                backgroundKilledCount = killRequests,
                confirmedStoppedPackages = confirmedStopped
            )
        }

    fun isPackageStopped(packageName: String): Boolean {
        val pm = context.packageManager
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }.getOrDefault(false)
    }

    private fun importanceToLabel(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "نشط الآن"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "خدمة نشطة"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "ظاهر بالذاكرة"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "خدمة خلفية"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "مخزن في RAM"
        else -> "عملية خلفية"
    }

    companion object {
        fun computeProcessScoreAndTier(
            livePssBytes: Long,
            totalStorageBytes: Long,
            totalForegroundMillis: Long,
            lastTimeUsedMillis: Long,
            nowMillis: Long,
            hasActiveService: Boolean
        ): Pair<Int, ProcessResourceTier> {
            var score = 15

            if (livePssBytes >= 180L * 1024L * 1024L) {
                score += 40
            } else if (livePssBytes >= 80L * 1024L * 1024L) {
                score += 25
            } else if (livePssBytes > 0L) {
                score += 15
            }

            if (totalStorageBytes >= 300L * 1024L * 1024L) {
                score += 25
            } else if (totalStorageBytes >= 120L * 1024L * 1024L) {
                score += 15
            } else if (totalStorageBytes >= 50L * 1024L * 1024L) {
                score += 8
            }

            if (totalForegroundMillis >= 2L * 60L * 60L * 1000L) {
                score += 25
            } else if (totalForegroundMillis >= 30L * 60L * 1000L) {
                score += 15
            } else if (totalForegroundMillis >= 5L * 60L * 1000L) {
                score += 8
            }

            if (lastTimeUsedMillis > 0L) {
                val ageMs = (nowMillis - lastTimeUsedMillis).coerceAtLeast(0L)
                when {
                    ageMs <= 30L * 60L * 1000L -> score += 20
                    ageMs <= 3L * 60L * 60L * 1000L -> score += 12
                    ageMs <= 24L * 60L * 60L * 1000L -> score += 5
                }
            }

            if (hasActiveService) score += 15

            val clamped = score.coerceIn(0, 100)
            val tier = when {
                clamped >= 70 -> ProcessResourceTier.CRITICAL
                clamped >= 45 -> ProcessResourceTier.HIGH
                else -> ProcessResourceTier.MODERATE
            }
            return clamped to tier
        }
    }
}
