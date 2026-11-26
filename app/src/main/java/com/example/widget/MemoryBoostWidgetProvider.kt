package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.data.CleanerPreferencesRepository
import com.example.data.local.CleanProDatabase
import com.example.engine.AppAnalyzerEngine
import com.example.engine.DeviceTelemetryMonitor
import com.example.engine.MemoryBoosterEngine
import com.example.model.Formatters
import com.example.scheduler.QuickCleanNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryBoostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_ONE_TAP_MEMORY_BOOST -> {
                val pendingResult = runCatching { goAsync() }.getOrNull()
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    val outcome = runCatching {
                        val start = System.currentTimeMillis()
                        val db = CleanProDatabase.getInstance(appContext)
                        val prefsRepo = CleanerPreferencesRepository(appContext, db.cleanHistoryDao())
                        val appAnalyzer = AppAnalyzerEngine(appContext)
                        val memoryBooster = MemoryBoosterEngine(appContext, appAnalyzer)

                        // 1. Scan and boost heavy background processes
                        val heavyProcesses = memoryBooster.scanHeavyBackgroundProcesses()
                        val execution = memoryBooster.boostBackgroundMemory(heavyProcesses)
                        val totalFreed = execution.ramFreedBytes + execution.cacheFreedBytes
                        val duration = (System.currentTimeMillis() - start).coerceAtLeast(100L)

                        if (totalFreed > 0L || execution.backgroundKilledCount > 0) {
                            prefsRepo.recordCleanSession(
                                bytesFreed = totalFreed,
                                filesRemoved = execution.confirmedStoppedPackages.size,
                                categoriesSummary = "تسريع فوري من ودجت الشاشة الرئيسية",
                                durationMillis = duration
                            )
                        }
                        execution to totalFreed
                    }

                    val (toastMessage, statusMessage) = outcome.fold(
                        onSuccess = { (execution, totalFreed) ->
                            when {
                                totalFreed > 0L -> {
                                    val freedLabel = Formatters.formatBytes(totalFreed)
                                    appContext.getString(R.string.widget_boosted_toast, freedLabel) to
                                        "تم تحرير $freedLabel بنجاح ✓"
                                }
                                execution.backgroundKilledCount > 0 -> {
                                    val stoppedMsg = appContext.getString(
                                        R.string.widget_boost_stopped_count,
                                        execution.backgroundKilledCount
                                    )
                                    stoppedMsg to stoppedMsg
                                }
                                else -> {
                                    val nothingMsg = appContext.getString(R.string.widget_boost_nothing)
                                    nothingMsg to nothingMsg
                                }
                            }
                        },
                        onFailure = {
                            val failedMsg = appContext.getString(R.string.widget_boost_failed)
                            failedMsg to failedMsg
                        }
                    )

                    runCatching {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(appContext, toastMessage, Toast.LENGTH_SHORT).show()
                        }
                    }

                    runCatching {
                        updateAllWidgets(appContext, statusMessage)
                        QuickCleanNotificationManager.showOrUpdate(appContext, statusMessage)
                    }
                    runCatching { pendingResult?.finish() }
                }
            }

            ACTION_REFRESH_WIDGET_TELEMETRY -> {
                updateAllWidgets(context.applicationContext, null)
            }
        }
    }

    companion object {
        const val ACTION_ONE_TAP_MEMORY_BOOST = "com.example.action.ONE_TAP_MEMORY_BOOST"
        const val ACTION_REFRESH_WIDGET_TELEMETRY = "com.example.action.REFRESH_WIDGET_TELEMETRY"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            customStatus: String? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_memory_boost)

            // Read live hardware snapshot
            val telemetry = DeviceTelemetryMonitor(context.applicationContext).readSnapshot()
            val ramPercentStr = Formatters.formatPercent(telemetry.ramUsageFraction)
            val freeRamStr = Formatters.formatBytes(telemetry.availRamBytes)
            val totalRamStr = Formatters.formatBytes(telemetry.totalRamBytes)

            views.setTextViewText(R.id.widget_ram_percent, ramPercentStr)
            views.setTextViewText(R.id.widget_ram_detail, "متاح $freeRamStr • إجمالي $totalRamStr")
            views.setTextViewText(
                R.id.widget_status_text,
                customStatus ?: context.getString(R.string.widget_ready)
            )

            // 1. One-tap boost button PendingIntent
            val boostIntent = Intent(context, MemoryBoostWidgetProvider::class.java).apply {
                action = ACTION_ONE_TAP_MEMORY_BOOST
            }
            val boostPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                boostIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_boost_button, boostPending)

            // 2. Refresh telemetry PendingIntent
            val refreshIntent = Intent(context, MemoryBoostWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET_TELEMETRY
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending)

            // 3. Header tap opens main app (optional navigation)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 3,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_row, openAppPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, customStatus: String? = null) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, MemoryBoostWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id, customStatus)
                }
            }
        }
    }
}
