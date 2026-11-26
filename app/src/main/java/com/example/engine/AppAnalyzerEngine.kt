package com.example.engine

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class AppAnalyzerEngine(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun getOwnCacheBytes(): Long = withContext(Dispatchers.IO) {
        folderSize(context.cacheDir) +
            folderSize(context.codeCacheDir) +
            (context.externalCacheDir?.let { folderSize(it) } ?: 0L)
    }

    val ownPackageName: String
        get() = context.packageName

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val before = getOwnCacheBytes()
        clearDirectoryContents(context.cacheDir)
        clearDirectoryContents(context.codeCacheDir)
        context.externalCacheDir?.let { clearDirectoryContents(it) }
        val after = getOwnCacheBytes()
        (before - after).coerceAtLeast(0L)
    }

    suspend fun queryPackageCacheBytes(packageName: String): Long? = withContext(Dispatchers.IO) {
        if (packageName == context.packageName) {
            return@withContext getOwnCacheBytes()
        }
        if (!hasUsageAccess() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext null
        }
        val storageStatsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                ?: return@withContext null
        runCatching {
            storageStatsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle()
            ).cacheBytes
        }.getOrNull()
    }

    suspend fun analyzeInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageAccess = hasUsageAccess()

        val usageMap: Map<String, UsageStats> = if (usageAccess) {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
            usageStatsManager
                ?.queryUsageStats(
                    UsageStatsManager.INTERVAL_MONTHLY,
                    cal.timeInMillis,
                    System.currentTimeMillis()
                )
                ?.associateBy { it.packageName }
                ?: emptyMap()
        } else {
            emptyMap()
        }

        val storageStatsManager = if (usageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()
        val userHandle = Process.myUserHandle()

        for (resolve in resolveInfos) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull() ?: continue

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            var appBytes = 0L
            var dataBytes = 0L
            var cacheBytes = 0L
            var hasStats = false

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
                    hasStats = true
                }
            }

            if (!hasStats) {
                runCatching {
                    val apkFile = File(appInfo.sourceDir)
                    if (apkFile.exists()) {
                        appBytes = apkFile.length()
                    }
                }
                if (pkg == context.packageName) {
                    cacheBytes = getOwnCacheBytes()
                    hasStats = true
                }
            }

            val usage = usageMap[pkg]
            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    appBytes = appBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    lastTimeUsedMillis = usage?.lastTimeUsed ?: 0L,
                    totalForegroundMillis = usage?.totalTimeInForeground ?: 0L,
                    isSystemApp = isSystem,
                    hasDetailedStats = hasStats
                )
            )
        }

        result.sortedByDescending { it.cacheBytes }
    }

    private fun folderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    private fun clearDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
