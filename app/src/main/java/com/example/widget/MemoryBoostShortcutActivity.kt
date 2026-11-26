package com.example.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.CleanerPreferencesRepository
import com.example.data.local.CleanProDatabase
import com.example.engine.AppAnalyzerEngine
import com.example.engine.MemoryBoosterEngine
import com.example.model.Formatters
import com.example.scheduler.QuickCleanNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trampoline Activity invoked when tapping the Launcher Shortcut from the home screen.
 * Runs the memory boost entirely in the background, displays feedback, and finishes immediately
 * WITHOUT showing any UI or opening the main app!
 */
class MemoryBoostShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                val start = System.currentTimeMillis()
                val db = CleanProDatabase.getInstance(appContext)
                val prefsRepo = CleanerPreferencesRepository(appContext, db.cleanHistoryDao())
                val appAnalyzer = AppAnalyzerEngine(appContext)
                val memoryBooster = MemoryBoosterEngine(appContext, appAnalyzer)

                val heavyProcesses = memoryBooster.scanHeavyBackgroundProcesses()
                val execution = memoryBooster.boostBackgroundMemory(heavyProcesses)
                val totalFreed = execution.ramFreedBytes + execution.cacheFreedBytes
                val duration = (System.currentTimeMillis() - start).coerceAtLeast(100L)

                if (totalFreed > 0L || execution.backgroundKilledCount > 0) {
                    prefsRepo.recordCleanSession(
                        bytesFreed = totalFreed,
                        filesRemoved = execution.confirmedStoppedPackages.size,
                        categoriesSummary = "تسريع فوري من اختصار الشاشة الرئيسية",
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
                MemoryBoostWidgetProvider.updateAllWidgets(appContext, statusMessage)
                QuickCleanNotificationManager.showOrUpdate(appContext, statusMessage)
            }

            withContext(Dispatchers.Main) {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }
}
