package com.example.engine

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import com.example.model.BatteryHealthEstimate
import com.example.model.DeviceTelemetry
import com.example.model.StorageHealthDiagnostics
import com.example.model.StorageHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

class DeviceTelemetryMonitor(private val context: Context) {

    fun observeTelemetry(
        intervalMs: Long = 2000L,
        isBatterySaverProvider: () -> Boolean = { false },
        heavyProcessCountProvider: () -> Int = { 0 }
    ): Flow<DeviceTelemetry> = flow {
        while (currentCoroutineContext().isActive) {
            emit(
                readSnapshot(
                    isBatterySaverEnabled = isBatterySaverProvider(),
                    heavyBackgroundProcessCount = heavyProcessCountProvider()
                )
            )
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun readSnapshot(
        isBatterySaverEnabled: Boolean = false,
        heavyBackgroundProcessCount: Int = 0
    ): DeviceTelemetry {
        val storageMetrics = readStorageMetrics()
        val usedStorage = (storageMetrics.totalBytes - storageMetrics.freeBytes).coerceAtLeast(0L)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = (totalRam - availRam).coerceAtLeast(0L)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else 0
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10f
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: "Li-ion"
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            ?: BatteryManager.BATTERY_HEALTH_GOOD

        val healthLabel = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "ممتازة"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "سخونة زائدة"
            BatteryManager.BATTERY_HEALTH_DEAD -> "تحتاج صيانة"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "جهد مرتفع"
            BatteryManager.BATTERY_HEALTH_COLD -> "باردة"
            else -> "جيدة"
        }

        val chargeStatusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "قيد الشحن"
            BatteryManager.BATTERY_STATUS_FULL -> "مكتمل الشحن"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "قيد التفريغ"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "متوقف عن الشحن"
            else -> if (isCharging) "قيد الشحن" else "قيد التفريغ"
        }

        val pluggedSourceLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "شاحن حائط (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "منفذ USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "شحن لاسلكي"
            else -> if (isCharging) "مصدر طاقة خارجي" else "طاقة البطارية"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawCurrent = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()
        val currentMa = rawCurrent?.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { raw ->
            if (abs(raw) >= 10_000) raw / 1000 else raw
        }

        val rawChargeCounterUah = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it > 0L }

        val systemChargeTimeRemainingMs = if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { batteryManager?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L)
        } else -1L

        val batteryEstimate = evaluateBatteryHealthAndTime(
            batteryPercent = batteryPercent,
            chargeStatus = status,
            pluggedType = plugged,
            tempCelsius = tempCelsius,
            voltageMv = voltageMv,
            hardwareHealthCode = health,
            currentMa = currentMa,
            chargeCounterUah = rawChargeCounterUah,
            systemChargeTimeRemainingMs = systemChargeTimeRemainingMs,
            isBatterySaverEnabled = isBatterySaverEnabled,
            heavyBackgroundProcessCount = heavyBackgroundProcessCount
        )

        val (cpuTemp, cpuTempSource) = readCpuTemperature(fallbackBatteryTemp = tempCelsius)
        val (thermalLabel, thermalSeverity, thermalHeadroom) = readThermalStatus()
        val (curCpuMhz, maxCpuMhz) = readCpuFrequenciesMhz()

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = if (Build.MODEL.startsWith(manufacturer, ignoreCase = true)) {
            Build.MODEL
        } else {
            "$manufacturer ${Build.MODEL}"
        }

        return DeviceTelemetry(
            totalStorageBytes = storageMetrics.totalBytes,
            freeStorageBytes = storageMetrics.freeBytes,
            usedStorageBytes = usedStorage,
            totalRamBytes = totalRam,
            availRamBytes = availRam,
            usedRamBytes = usedRam,
            isLowMemory = memInfo.lowMemory,
            ramLowThresholdBytes = memInfo.threshold,
            batteryPercent = batteryPercent,
            batteryTempCelsius = tempCelsius,
            batteryVoltageMv = voltageMv,
            isCharging = isCharging,
            batteryHealthLabel = healthLabel,
            batteryChargeStatusLabel = chargeStatusLabel,
            batteryPluggedSourceLabel = pluggedSourceLabel,
            batteryTechnology = technology,
            batteryCurrentMa = currentMa,
            batteryEstimatedCapacityMah = batteryEstimate.estimatedCapacityMah,
            batteryHealthScore = batteryEstimate.healthScore,
            batteryThermalStatusLabel = batteryEstimate.thermalStatusLabel,
            batteryTimeRemainingMillis = batteryEstimate.remainingTimeMillis,
            isChargeTimeEstimate = batteryEstimate.isChargeTime,
            isHardwareChargeEstimate = batteryEstimate.isHardwareMeasured,
            deviceModel = model,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpuTempCelsius = cpuTemp,
            cpuTempSourceLabel = cpuTempSource,
            thermalStatusLabel = thermalLabel,
            thermalSeverityLevel = thermalSeverity,
            thermalHeadroom = thermalHeadroom,
            cpuCurrentFreqMhz = curCpuMhz,
            cpuMaxFreqMhz = maxCpuMhz,
            uptimeMillis = SystemClock.elapsedRealtime(),
            storageBlockSizeBytes = storageMetrics.blockSizeBytes,
            storageTotalBlocks = storageMetrics.totalBlocks,
            storageAvailableBlocks = storageMetrics.availableBlocks,
            appCacheQuotaBytes = storageMetrics.cacheQuotaBytes
        )
    }

    suspend fun runStorageHealthDiagnostics(ownCacheBytes: Long): StorageHealthDiagnostics =
        withContext(Dispatchers.IO) {
            val metrics = readStorageMetrics()
            val ioProbe = measureSafeCacheIoSpeed()
            evaluateStorageHealth(
                totalBytes = metrics.totalBytes,
                freeBytes = metrics.freeBytes,
                ownCacheBytes = ownCacheBytes,
                cacheQuotaBytes = metrics.cacheQuotaBytes,
                blockSizeBytes = metrics.blockSizeBytes,
                writeSpeedMbPerSec = ioProbe.writeMbPerSec,
                readSpeedMbPerSec = ioProbe.readMbPerSec,
                ioLatencyMs = ioProbe.latencyMs,
                timestampMillis = System.currentTimeMillis()
            )
        }

    private data class StorageMetrics(
        val totalBytes: Long,
        val freeBytes: Long,
        val blockSizeBytes: Long,
        val totalBlocks: Long,
        val availableBlocks: Long,
        val cacheQuotaBytes: Long
    )

    private data class IoProbeResult(
        val writeMbPerSec: Float?,
        val readMbPerSec: Float?,
        val latencyMs: Long?
    )

    private fun readStorageMetrics(): StorageMetrics {
        val stat = StatFs(Environment.getDataDirectory().path)
        var total = stat.totalBytes
        var free = stat.availableBytes
        var cacheQuota = 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (statsManager != null) {
                    val sysTotal = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val sysFree = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (sysTotal > 0L) {
                        total = sysTotal
                        free = sysFree
                    }
                }
            }
            runCatching {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (storageManager != null) {
                    val uuid = storageManager.getUuidForPath(context.filesDir)
                    cacheQuota = storageManager.getCacheQuotaBytes(uuid)
                }
            }
        }

        return StorageMetrics(
            totalBytes = total,
            freeBytes = free,
            blockSizeBytes = stat.blockSizeLong.coerceAtLeast(512L),
            totalBlocks = stat.blockCountLong,
            availableBlocks = stat.availableBlocksLong,
            cacheQuotaBytes = cacheQuota
        )
    }

    private fun measureSafeCacheIoSpeed(): IoProbeResult {
        val probeFile = File(context.cacheDir, "cleanpro_io_diag.tmp")
        return try {
            val payloadSize = 256 * 1024 // 256 KB quick non-intrusive probe
            val payload = ByteArray(payloadSize) { (it and 0x7F).toByte() }
            val readBuf = ByteArray(payloadSize)

            val writeStart = System.nanoTime()
            FileOutputStream(probeFile).use { fos ->
                fos.write(payload)
                fos.fd.sync()
            }
            val writeNs = (System.nanoTime() - writeStart).coerceAtLeast(1L)

            val readStart = System.nanoTime()
            FileInputStream(probeFile).use { fis ->
                var offset = 0
                while (offset < payloadSize) {
                    val n = fis.read(readBuf, offset, payloadSize - offset)
                    if (n <= 0) break
                    offset += n
                }
            }
            val readNs = (System.nanoTime() - readStart).coerceAtLeast(1L)

            val mb = payloadSize.toDouble() / (1024.0 * 1024.0)
            val writeMbPerSec = (mb / (writeNs / 1_000_000_000.0)).toFloat().coerceIn(1f, 5000f)
            val readMbPerSec = (mb / (readNs / 1_000_000_000.0)).toFloat().coerceIn(1f, 8000f)
            val totalLatencyMs = ((writeNs + readNs) / 1_000_000L).coerceAtLeast(1L)

            IoProbeResult(
                writeMbPerSec = writeMbPerSec,
                readMbPerSec = readMbPerSec,
                latencyMs = totalLatencyMs
            )
        } catch (_: Exception) {
            IoProbeResult(null, null, null)
        } finally {
            runCatching { if (probeFile.exists()) probeFile.delete() }
        }
    }

    private fun readCpuTemperature(fallbackBatteryTemp: Float): Pair<Float?, String> {
        runCatching {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                val zones = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") }
                    ?.sortedBy { it.name }
                    .orEmpty()
                for (zone in zones) {
                    val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull().orEmpty()
                    if (type.contains("cpu") || type.contains("soc") || type.contains("ap") ||
                        type.contains("tsens") || type.contains("mtkts") || type.contains("big") ||
                        type.contains("little")
                    ) {
                        val raw = runCatching { File(zone, "temp").readText().trim().toFloatOrNull() }.getOrNull()
                        val normalized = raw?.let { normalizeThermalTempCelsius(it) }
                        if (normalized != null) {
                            return normalized to "مستشعر المعالج ($type)"
                        }
                    }
                }
            }
        }
        return if (fallbackBatteryTemp > 0f) {
            null to "مستشعر البطارية / الهيكل"
        } else {
            null to "حالة النظام الحرارية"
        }
    }

    private fun readThermalStatus(): Triple<String, Int, Float?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Triple("طبيعية", 0, null)
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return Triple("طبيعية", 0, null)

        val status = runCatching { powerManager.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        val (label, severity) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "طبيعية" to 0
            PowerManager.THERMAL_STATUS_LIGHT -> "دافئة قليلاً" to 1
            PowerManager.THERMAL_STATUS_MODERATE -> "مرتفعة نسبياً" to 2
            PowerManager.THERMAL_STATUS_SEVERE -> "مرتفعة جداً" to 3
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "حرارة حرجة" to 4
            else -> "طبيعية" to 0
        }

        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val h = powerManager.getThermalHeadroom(10)
                if (h.isFinite() && h >= 0f) h else null
            }.getOrNull()
        } else null

        return Triple(label, severity, headroom)
    }

    private fun readCpuFrequenciesMhz(): Pair<Int?, Int?> {
        return runCatching {
            val curList = mutableListOf<Int>()
            var maxFreqKhz = 0
            for (core in 0 until 8) {
                val curFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                if (curFile.exists() && curFile.canRead()) {
                    curFile.readText().trim().toIntOrNull()?.let { curList.add(it) }
                }
                val maxFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                if (maxFile.exists() && maxFile.canRead()) {
                    maxFile.readText().trim().toIntOrNull()?.let {
                        if (it > maxFreqKhz) maxFreqKhz = it
                    }
                }
            }
            val avgCurMhz = if (curList.isNotEmpty()) (curList.average() / 1000.0).roundToInt() else null
            val maxMhz = if (maxFreqKhz > 0) (maxFreqKhz / 1000.0).roundToInt() else null
            avgCurMhz to maxMhz
        }.getOrDefault(null to null)
    }

    companion object {
        fun normalizeThermalTempCelsius(rawValue: Float): Float? {
            if (!rawValue.isFinite()) return null
            val normalized = if (rawValue > 200f) rawValue / 1000f else rawValue
            return if (normalized in 10f..125f) normalized else null
        }

        fun evaluateStorageHealth(
            totalBytes: Long,
            freeBytes: Long,
            ownCacheBytes: Long,
            cacheQuotaBytes: Long,
            blockSizeBytes: Long,
            writeSpeedMbPerSec: Float?,
            readSpeedMbPerSec: Float?,
            ioLatencyMs: Long?,
            timestampMillis: Long
        ): StorageHealthDiagnostics {
            val freePct = if (totalBytes > 0L) {
                ((freeBytes.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
            } else 100f

            val cachePressure = if (cacheQuotaBytes > 0L) {
                (ownCacheBytes.toFloat() / cacheQuotaBytes.toFloat()).coerceIn(0f, 2f)
            } else 0f

            var score = 100
            when {
                freePct < 8f -> score -= 45
                freePct < 15f -> score -= 25
                freePct < 25f -> score -= 10
            }
            if (ioLatencyMs != null && ioLatencyMs > 120L) score -= 15
            if (cachePressure > 0.85f) score -= 10
            val clampedScore = score.coerceIn(0, 100)

            val fragmentationRisk = freePct < 15f
            val status = when {
                clampedScore < 60 || freePct < 10f -> StorageHealthStatus.CRITICAL
                clampedScore < 85 || freePct < 20f -> StorageHealthStatus.ATTENTION
                else -> StorageHealthStatus.HEALTHY
            }

            val recommendation = when (status) {
                StorageHealthStatus.CRITICAL ->
                    "المساحة الحرة منخفضة جداً (${String.format("%.1f", freePct)}%)، ما يرفع إجهاد الذاكرة الفلاشية ويبطئ الكتابة. يُنصح بتنظيف الملفات الكبيرة فوراً."
                StorageHealthStatus.ATTENTION ->
                    "المساحة المتبقية متوسطة (${String.format("%.1f", freePct)}%). تفريغ الكاش والملفات المكررة يحافظ على سرعة التخزين."
                StorageHealthStatus.HEALTHY ->
                    "صحة التخزين ممتازة (${String.format("%.1f", freePct)}% مساحة حرة) وسرعة الاستجابة مستقرة."
            }

            return StorageHealthDiagnostics(
                status = status,
                healthScore = clampedScore,
                freeSpacePercent = freePct,
                writeSpeedMbPerSec = writeSpeedMbPerSec,
                readSpeedMbPerSec = readSpeedMbPerSec,
                ioLatencyMs = ioLatencyMs,
                blockSizeBytes = blockSizeBytes,
                isFragmentationRiskHigh = fragmentationRisk,
                cachePressureRatio = cachePressure,
                recommendationAr = recommendation,
                lastCheckedMillis = timestampMillis
            )
        }

        fun evaluateBatteryHealthAndTime(
            batteryPercent: Int,
            chargeStatus: Int,
            pluggedType: Int,
            tempCelsius: Float,
            voltageMv: Int,
            hardwareHealthCode: Int,
            currentMa: Int?,
            chargeCounterUah: Long?,
            systemChargeTimeRemainingMs: Long,
            isBatterySaverEnabled: Boolean,
            heavyBackgroundProcessCount: Int
        ): BatteryHealthEstimate {
            val pct = batteryPercent.coerceIn(0, 100)
            val isCharging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargeStatus == BatteryManager.BATTERY_STATUS_FULL

            val thermalStatus = when {
                tempCelsius in 1f..14.9f -> "باردة"
                tempCelsius >= 41.0f -> "ساخنة (يُنصح بالتبريد)"
                tempCelsius >= 36.5f -> "دافئة"
                else -> "مثالية"
            }

            var healthScore = 100
            when (hardwareHealthCode) {
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> healthScore -= 35
                BatteryManager.BATTERY_HEALTH_DEAD -> healthScore -= 60
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> healthScore -= 30
                BatteryManager.BATTERY_HEALTH_COLD -> healthScore -= 15
            }
            if (tempCelsius >= 43.0f) {
                healthScore -= 25
            } else if (tempCelsius >= 39.5f) {
                healthScore -= 12
            } else if (tempCelsius in 1f..10f) {
                healthScore -= 10
            }
            if (voltageMv in 1..3250) {
                healthScore -= 10
            }
            val clampedScore = healthScore.coerceIn(0, 100)

            val estimatedCapacityMah = if (chargeCounterUah != null && chargeCounterUah > 0L && pct >= 5) {
                val rawMah = ((chargeCounterUah / 1000.0) / (pct / 100.0)).roundToInt()
                if (rawMah in 800..15_000) rawMah else null
            } else null

            var remainingMillis = 0L
            var isHardwareMeasured = false

            if (isCharging) {
                if (chargeStatus == BatteryManager.BATTERY_STATUS_FULL || pct >= 100) {
                    remainingMillis = 0L
                    isHardwareMeasured = systemChargeTimeRemainingMs >= 0L
                } else if (systemChargeTimeRemainingMs > 0L) {
                    remainingMillis = systemChargeTimeRemainingMs.coerceIn(60_000L, 12L * 3600_000L)
                    isHardwareMeasured = true
                } else {
                    val absCurrentMa = currentMa?.let { abs(it) } ?: 0
                    if (absCurrentMa >= 150 && estimatedCapacityMah != null) {
                        val missingMah = estimatedCapacityMah * ((100 - pct) / 100.0)
                        val hours = (missingMah / absCurrentMa.toDouble()) * if (pct >= 80) 1.25 else 1.0
                        remainingMillis = (hours * 3600_000.0).toLong().coerceIn(2L * 60_000L, 10L * 3600_000L)
                        isHardwareMeasured = true
                    } else {
                        val baseFullChargeHours = when (pluggedType) {
                            BatteryManager.BATTERY_PLUGGED_AC -> 1.65
                            BatteryManager.BATTERY_PLUGGED_WIRELESS -> 2.40
                            BatteryManager.BATTERY_PLUGGED_USB -> 4.10
                            else -> 2.00
                        }
                        val tailSlowdown = if (pct >= 80) 1.2 else 1.0
                        val hours = baseFullChargeHours * ((100 - pct) / 100.0) * tailSlowdown
                        remainingMillis = (hours * 3600_000.0).toLong().coerceAtLeast(2L * 60_000L)
                    }
                }
            } else {
                val absCurrentMa = currentMa?.let { abs(it) } ?: 0
                if (absCurrentMa >= 50 && chargeCounterUah != null && chargeCounterUah > 0L) {
                    val currentMah = chargeCounterUah / 1000.0
                    val saverBoost = if (isBatterySaverEnabled) 1.15 else 1.0
                    val hours = (currentMah / absCurrentMa.toDouble()) * saverBoost
                    remainingMillis = (hours * 3600_000.0).toLong().coerceIn(10L * 60_000L, 72L * 3600_000L)
                    isHardwareMeasured = true
                } else {
                    val baseHoursAtFull = 14.0
                    val saverMultiplier = if (isBatterySaverEnabled) 1.22 else 1.0
                    val heavyPenalty = (1.0 - (heavyBackgroundProcessCount.coerceAtMost(6) * 0.05)).coerceAtLeast(0.65)
                    val thermalPenalty = if (tempCelsius >= 40f) 0.88 else 1.0
                    val hours = baseHoursAtFull * (pct / 100.0) * saverMultiplier * heavyPenalty * thermalPenalty
                    remainingMillis = if (pct == 0) {
                        0L
                    } else {
                        (hours * 3600_000.0).toLong().coerceIn(5L * 60_000L, 48L * 3600_000L)
                    }
                }
            }

            return BatteryHealthEstimate(
                healthScore = clampedScore,
                thermalStatusLabel = thermalStatus,
                estimatedCapacityMah = estimatedCapacityMah,
                remainingTimeMillis = remainingMillis,
                isChargeTime = isCharging,
                isHardwareMeasured = isHardwareMeasured
            )
        }
    }
}
