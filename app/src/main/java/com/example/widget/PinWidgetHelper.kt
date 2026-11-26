package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.example.R

object PinWidgetHelper {

    fun isPinShortcutSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        return shortcutManager.isRequestPinShortcutSupported
    }

    fun isPinWidgetSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun requestPinShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val shortcutIntent = Intent(context, MemoryBoostShortcutActivity::class.java).apply {
            action = "com.example.action.SHORTCUT_MEMORY_BOOST"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pinShortcutInfo = ShortcutInfo.Builder(context, "one_tap_memory_boost_pinned")
            .setShortLabel(context.getString(R.string.shortcut_boost_short))
            .setLongLabel(context.getString(R.string.shortcut_boost_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_cleaner_emblem))
            .setIntent(shortcutIntent)
            .build()

        return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
    }

    fun requestPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, MemoryBoostWidgetProvider::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }
}
