package com.example.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CleanerPreferencesRepository
import com.example.data.local.CleanProDatabase
import com.example.engine.AppAnalyzerEngine
import com.example.engine.DeviceTelemetryMonitor
import com.example.engine.StorageScannerEngine
import com.example.model.CleaningScheduleFrequency
import com.example.model.FileCategory
import com.example.model.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReminderScheduler {
    private const val JOB_ID = 4401

    fun scheduleAutoClean(
        context: Context,
        frequency: CleaningScheduleFrequency,
        requireCharging: Boolean
    ) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val intervalMillis = frequency.intervalHours * 60L * 60L * 1000L
        val component = ComponentName(context, CleanReminderJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID, component)
            .setPeriodic(intervalMillis)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(requireCharging)
            .build()
        scheduler.schedule(jobInfo)
    }

    fun cancelAutoClean(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        scheduler.cancel(JOB_ID)
    }
}

class CleanReminderJobService : JobService() {

    companion object {
        private const val CHANNEL_ID = "clean_pro_reminders"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        activeJob = serviceScope.launch {
            runCatching {
                val start = System.currentTimeMillis()
                val db = CleanProDatabase.getInstance(applicationContext)
                val prefsRepo = CleanerPreferencesRepository(applicationContext, db.cleanHistoryDao())
                val prefs = prefsRepo.preferencesFlow.first()

                val appAnalyzer = AppAnalyzerEngine(applicationContext)
                val storageScanner = StorageScannerEngine(applicationContext)

                val cacheFreed = appAnalyzer.clearOwnCache()

                val validSafUris = storageScanner.resolveSafFolders(prefs.safFolderUris)
                    .filter { it.hasValidPermission }
                    .map { it.uriString }
                    .toSet()

                var deletedFiles = 0
                var safFreed = 0L
                if (validSafUris.isNotEmpty()) {
                    val scanned = storageScanner.scanSafFolders(
                        folderUris = validSafUris,
                        largeFileThresholdMb = prefs.largeFileThresholdMb,
                        onProgress = { _, _ -> }
                    )
                    val safeTempFiles = scanned.filter {
                        it.category == FileCategory.TEMP_AND_EMPTY && it.isDeletableSaf
                    }
                    if (safeTempFiles.isNotEmpty()) {
                        val (count, bytes) = storageScanner.deleteSafDocuments(safeTempFiles)
                        deletedFiles = count
                        safFreed = bytes
                    }
                }

                val totalFreed = cacheFreed + safFreed
                val now = System.currentTimeMillis()
                prefsRepo.updateLastAutoCleanTimestamp(now)

                if (totalFreed > 0L || deletedFiles > 0) {
                    prefsRepo.recordCleanSession(
                        bytesFreed = totalFreed,
                        filesRemoved = deletedFiles,
                        categoriesSummary = "تنظيف تلقائي مجدول (${prefs.autoCleanFrequency.labelAr})",
                        durationMillis = (now - start).coerceAtLeast(100L)
                    )
                }

                notifyIfPermitted(totalFreed, deletedFiles, prefs.autoCleanFrequency)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun notifyIfPermitted(
        bytesFreed: Long,
        deletedFiles: Int,
        frequency: CleaningScheduleFrequency
    ) {
        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!canNotify) return

        val telemetry = DeviceTelemetryMonitor(applicationContext).readSnapshot()
        val usagePercent = (telemetry.storageUsageFraction * 100f).toInt()
        val body = when {
            bytesFreed > 0L ->
                "تم التنظيف التلقائي (${frequency.labelAr}): تم تحرير ${Formatters.formatBytes(bytesFreed)} ($deletedFiles ملف)."
            usagePercent >= 80 ->
                getString(R.string.notification_body_high_usage, usagePercent)
            else ->
                getString(R.string.notification_body_routine)
        }
        showNotification(body)
    }

    private fun showNotification(body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cleaner_emblem)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
