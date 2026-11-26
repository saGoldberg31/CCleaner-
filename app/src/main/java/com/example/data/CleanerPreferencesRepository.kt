package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.CleanHistoryDao
import com.example.data.local.CleanHistoryEntity
import com.example.model.CleaningScheduleFrequency
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cleanerDataStore: DataStore<Preferences> by preferencesDataStore(name = "cleaner_prefs")

data class CleanerPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val largeFileThresholdMb: Int = 10,
    val autoCleanScheduleEnabled: Boolean = false,
    val autoCleanFrequency: CleaningScheduleFrequency = CleaningScheduleFrequency.DAILY,
    val autoCleanOnlyWhenCharging: Boolean = false,
    val lastAutoCleanTimestamp: Long = 0L,
    val safFolderUris: Set<String> = emptySet(),
    val lastScanTimestamp: Long = 0L,
    val batterySaverEnabled: Boolean = false,
    val quickCleanNotificationEnabled: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false
)

class CleanerPreferencesRepository(
    private val context: Context,
    private val historyDao: CleanHistoryDao
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LARGE_FILE_THRESHOLD_MB = intPreferencesKey("large_file_threshold_mb")
        val AUTO_CLEAN_ENABLED = booleanPreferencesKey("auto_remind_enabled")
        val AUTO_CLEAN_FREQUENCY = stringPreferencesKey("auto_clean_frequency")
        val AUTO_CLEAN_CHARGING_ONLY = booleanPreferencesKey("auto_clean_charging_only")
        val LAST_AUTO_CLEAN_TIMESTAMP = longPreferencesKey("last_auto_clean_timestamp")
        val SAF_FOLDER_URIS = stringSetPreferencesKey("saf_folder_uris")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val BATTERY_SAVER_ENABLED = booleanPreferencesKey("battery_saver_enabled")
        val QUICK_CLEAN_NOTIFICATION = booleanPreferencesKey("quick_clean_notification")
        val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    }

    val preferencesFlow: Flow<CleanerPreferences> = context.cleanerDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK)
        val freqStr = prefs[Keys.AUTO_CLEAN_FREQUENCY] ?: CleaningScheduleFrequency.DAILY.name
        val frequency = runCatching { CleaningScheduleFrequency.valueOf(freqStr) }
            .getOrDefault(CleaningScheduleFrequency.DAILY)
        CleanerPreferences(
            themeMode = themeMode,
            largeFileThresholdMb = prefs[Keys.LARGE_FILE_THRESHOLD_MB] ?: 10,
            autoCleanScheduleEnabled = prefs[Keys.AUTO_CLEAN_ENABLED] ?: false,
            autoCleanFrequency = frequency,
            autoCleanOnlyWhenCharging = prefs[Keys.AUTO_CLEAN_CHARGING_ONLY] ?: false,
            lastAutoCleanTimestamp = prefs[Keys.LAST_AUTO_CLEAN_TIMESTAMP] ?: 0L,
            safFolderUris = prefs[Keys.SAF_FOLDER_URIS] ?: emptySet(),
            lastScanTimestamp = prefs[Keys.LAST_SCAN_TIMESTAMP] ?: 0L,
            batterySaverEnabled = prefs[Keys.BATTERY_SAVER_ENABLED] ?: false,
            quickCleanNotificationEnabled = prefs[Keys.QUICK_CLEAN_NOTIFICATION] ?: false,
            liquidGlassEnabled = prefs[Keys.LIQUID_GLASS_ENABLED] ?: true,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false
        )
    }

    val historyFlow: Flow<List<CleanHistoryEntity>> = historyDao.getAllHistory()
    val totalFreedFlow: Flow<Long> = historyDao.getTotalBytesFreed()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.cleanerDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLargeFileThresholdMb(mb: Int) {
        context.cleanerDataStore.edit { it[Keys.LARGE_FILE_THRESHOLD_MB] = mb.coerceIn(5, 250) }
    }

    suspend fun setAutoCleanSchedule(
        enabled: Boolean,
        frequency: CleaningScheduleFrequency,
        onlyWhenCharging: Boolean
    ) {
        context.cleanerDataStore.edit {
            it[Keys.AUTO_CLEAN_ENABLED] = enabled
            it[Keys.AUTO_CLEAN_FREQUENCY] = frequency.name
            it[Keys.AUTO_CLEAN_CHARGING_ONLY] = onlyWhenCharging
        }
    }

    suspend fun updateLastAutoCleanTimestamp(timestamp: Long) {
        context.cleanerDataStore.edit { it[Keys.LAST_AUTO_CLEAN_TIMESTAMP] = timestamp }
    }

    suspend fun setBatterySaverEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit {
            it[Keys.BATTERY_SAVER_ENABLED] = enabled
        }
    }

    suspend fun setQuickCleanNotificationEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit {
            it[Keys.QUICK_CLEAN_NOTIFICATION] = enabled
        }
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit {
            it[Keys.LIQUID_GLASS_ENABLED] = enabled
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit {
            it[Keys.CLOUD_SYNC_ENABLED] = enabled
        }
    }

    suspend fun addSafFolderUri(uriString: String) {
        context.cleanerDataStore.edit { prefs ->
            val current = prefs[Keys.SAF_FOLDER_URIS] ?: emptySet()
            prefs[Keys.SAF_FOLDER_URIS] = current + uriString
        }
    }

    suspend fun removeSafFolderUri(uriString: String) {
        context.cleanerDataStore.edit { prefs ->
            val current = prefs[Keys.SAF_FOLDER_URIS] ?: emptySet()
            prefs[Keys.SAF_FOLDER_URIS] = current - uriString
        }
    }

    suspend fun updateLastScanTimestamp(timestamp: Long) {
        context.cleanerDataStore.edit { it[Keys.LAST_SCAN_TIMESTAMP] = timestamp }
    }

    suspend fun recordCleanSession(
        bytesFreed: Long,
        filesRemoved: Int,
        categoriesSummary: String,
        durationMillis: Long
    ) {
        historyDao.insertHistory(
            CleanHistoryEntity(
                bytesFreed = bytesFreed,
                filesRemoved = filesRemoved,
                categoriesSummary = categoriesSummary,
                durationMillis = durationMillis
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }
}
