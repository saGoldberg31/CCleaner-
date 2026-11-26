package com.example.engine

import android.content.Context
import com.example.model.BackgroundProcessInfo
import com.example.model.ProcessResourceTier

data class BatteryDrainReport(
    val highDrainAppCount: Int,
    val thermalDrainSeverity: String,
    val topDrainApps: List<BackgroundProcessInfo>,
    val recommendationAr: String
)

object BatteryDrainAnalyzer {
    fun analyzeDrain(processes: List<BackgroundProcessInfo>, cpuTempCelsius: Float): BatteryDrainReport {
        val highDrain = processes.filter { it.resourceTier == ProcessResourceTier.CRITICAL || it.resourceTier == ProcessResourceTier.HIGH }
            .sortedByDescending { it.processMemoryBytes }

        val severity = when {
            cpuTempCelsius >= 41.5f || highDrain.size >= 4 -> "مرتفع جداً (استنزاف ملحوظ للبطارية)"
            cpuTempCelsius >= 38.0f || highDrain.size >= 2 -> "متوسط (نشاط خلفية نشط)"
            else -> "طبيعي ومستقر"
        }

        val recommendation = when {
            cpuTempCelsius >= 41.5f -> "حرارة المعالج مرتفعة واستهلاك الخلفية كبير. يُنصح بإيقاف التطبيقات الثقيلة وتبريد الجهاز."
            highDrain.isNotEmpty() -> "تم رصد تطبيقات تستهلك موارد كبيرة في الخلفية. إيقافها سيوفر حتى 25% من طاقة البطارية."
            else -> "لا توجد عمليات نشطة مستنزفة للطاقة حالياً. البطارية في حالة ممتازة."
        }

        return BatteryDrainReport(
            highDrainAppCount = highDrain.size,
            thermalDrainSeverity = severity,
            topDrainApps = highDrain.take(3),
            recommendationAr = recommendation
        )
    }
}
