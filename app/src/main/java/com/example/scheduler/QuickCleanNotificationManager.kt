package com.example.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.engine.DeviceTelemetryMonitor
import com.example.model.Formatters

object QuickCleanNotificationManager {
    private const val CHANNEL_ID = "clean_pro_quick_bar"
    private const val NOTIFICATION_ID = 1002
    const val ACTION_QUICK_CLEAN_CACHE = "com.example.action.QUICK_CLEAN_CACHE"

    fun showOrUpdate(context: Context, customStatus: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.quick_clean_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.quick_clean_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val telemetry = DeviceTelemetryMonitor(context.applicationContext).readSnapshot()
        val ramPct = Formatters.formatPercent(telemetry.ramUsageFraction)
        val freeStorage = Formatters.formatBytes(telemetry.freeStorageBytes)

        val contentText = customStatus ?: "متاح: $freeStorage • ذاكرة RAM مستخدمة: $ramPct"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanBroadcastIntent = Intent(context, QuickCleanActionReceiver::class.java).apply {
            action = ACTION_QUICK_CLEAN_CACHE
        }
        val cleanPending = PendingIntent.getBroadcast(
            context,
            202,
            cleanBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cleaner_emblem)
            .setContentTitle(context.getString(R.string.quick_clean_notification_title))
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_cleaner_emblem,
                context.getString(R.string.quick_clean_action_cache),
                cleanPending
            )
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(NOTIFICATION_ID)
    }
}
