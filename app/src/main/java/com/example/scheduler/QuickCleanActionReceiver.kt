package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.CleanerPreferencesRepository
import com.example.data.local.CleanProDatabase
import com.example.engine.AppAnalyzerEngine
import com.example.model.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickCleanActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != QuickCleanNotificationManager.ACTION_QUICK_CLEAN_CACHE) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val start = System.currentTimeMillis()
                val db = CleanProDatabase.getInstance(appContext)
                val prefsRepo = CleanerPreferencesRepository(appContext, db.cleanHistoryDao())
                val appAnalyzer = AppAnalyzerEngine(appContext)
                val freed = appAnalyzer.clearOwnCache()
                if (freed > 0L) {
                    prefsRepo.recordCleanSession(
                        bytesFreed = freed,
                        filesRemoved = 0,
                        categoriesSummary = "تنظيف سريع من شريط الإشعارات",
                        durationMillis = (System.currentTimeMillis() - start).coerceAtLeast(100L)
                    )
                }
                val message = if (freed > 0L) {
                    "تم تحرير ${Formatters.formatBytes(freed)} من الكاش بنجاح!"
                } else {
                    "الذاكرة المؤقتة نظيفة بالفعل ✓"
                }
                QuickCleanNotificationManager.showOrUpdate(appContext, customStatus = message)
            }
            pendingResult.finish()
        }
    }
}
