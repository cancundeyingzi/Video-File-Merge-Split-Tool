package com.ccdyz.tools.utils

import android.content.Context
import android.content.SharedPreferences
import com.ccdyz.tools.data.database.entities.HostHistory
import com.ccdyz.tools.data.database.entities.ScanHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class AppPreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "tools_app_prefs", 
        Context.MODE_PRIVATE
    )
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    companion object {
        // History related keys
        private const val KEY_SCAN_HISTORY = "scan_history"
        private const val KEY_HOST_HISTORY = "host_history"
        
        // App settings related keys
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTO_CLEAR_HISTORY = "auto_clear_history"
        private const val KEY_HISTORY_LIMIT = "history_limit"
        private const val KEY_SCAN_TIMEOUT = "scan_timeout"
        private const val KEY_CONCURRENT_THREADS = "concurrent_threads"
        private const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
        private const val KEY_CACHE_SIZE_LIMIT = "cache_size_limit"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_APP_VERSION = "app_version"
        
        // Default values
        const val DEFAULT_HISTORY_LIMIT = 50
        const val DEFAULT_SCAN_TIMEOUT = 3000L
        const val DEFAULT_CONCURRENT_THREADS = 100
        const val DEFAULT_CACHE_SIZE_LIMIT = 100 // MB
    }
    
    // Theme Settings
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
    
    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
    
    // History Settings
    var isAutoClearHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEAR_HISTORY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEAR_HISTORY, value).apply()
    
    var historyLimit: Int
        get() = prefs.getInt(KEY_HISTORY_LIMIT, DEFAULT_HISTORY_LIMIT)
        set(value) = prefs.edit().putInt(KEY_HISTORY_LIMIT, value).apply()
    
    // Scanner Settings
    var scanTimeout: Long
        get() = prefs.getLong(KEY_SCAN_TIMEOUT, DEFAULT_SCAN_TIMEOUT)
        set(value) = prefs.edit().putLong(KEY_SCAN_TIMEOUT, value).apply()
    
    var concurrentThreads: Int
        get() = prefs.getInt(KEY_CONCURRENT_THREADS, DEFAULT_CONCURRENT_THREADS)
        set(value) = prefs.edit().putInt(KEY_CONCURRENT_THREADS, value).apply()
    
    // Notification Settings
    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_NOTIFICATIONS, value).apply()
    
    // Cache Settings
    var cacheSizeLimit: Int
        get() = prefs.getInt(KEY_CACHE_SIZE_LIMIT, DEFAULT_CACHE_SIZE_LIMIT)
        set(value) = prefs.edit().putInt(KEY_CACHE_SIZE_LIMIT, value).apply()
    
    // App Info
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    var appVersion: String
        get() = prefs.getString(KEY_APP_VERSION, "1.0.0") ?: "1.0.0"
        set(value) = prefs.edit().putString(KEY_APP_VERSION, value).apply()
        
    init {
        // If first launch, initialize default settings
        if (isFirstLaunch) {
            resetToDefaults()
            isFirstLaunch = false
        }
    }
    
    // History management methods - used by ScanRepository
    suspend fun saveScanHistory(history: List<ScanHistory>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        val limitedHistory = if (isAutoClearHistoryEnabled && history.size > historyLimit) {
            history.takeLast(historyLimit)
        } else {
            history
        }
        
        limitedHistory.forEach { scan ->
            val jsonObject = JSONObject().apply {
                put("id", scan.id)
                put("hostname", scan.hostname)
                put("portRange", scan.portRange)
                put("openPorts", scan.openPorts)
                put("totalPorts", scan.totalPorts)
                put("openPortsCount", scan.openPortsCount)
                put("scanTime", dateFormat.format(scan.scanTime))
                put("duration", scan.duration)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_SCAN_HISTORY, jsonArray.toString()).apply()
    }
    
    suspend fun loadScanHistory(): List<ScanHistory> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_SCAN_HISTORY, null) ?: return@withContext emptyList()
        
        try {
            val jsonArray = JSONArray(jsonString)
            val history = mutableListOf<ScanHistory>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val scanTime = try {
                    dateFormat.parse(jsonObject.getString("scanTime")) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
                
                history.add(
                    ScanHistory(
                        id = jsonObject.getString("id"),
                        hostname = jsonObject.getString("hostname"),
                        portRange = jsonObject.getString("portRange"),
                        openPorts = jsonObject.getString("openPorts"),
                        totalPorts = jsonObject.getInt("totalPorts"),
                        openPortsCount = jsonObject.getInt("openPortsCount"),
                        scanTime = scanTime,
                        duration = jsonObject.getLong("duration")
                    )
                )
            }
            
            history
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun saveHostHistory(history: List<HostHistory>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        val limitedHistory = if (isAutoClearHistoryEnabled && history.size > historyLimit) {
            history.takeLast(historyLimit)
        } else {
            history
        }
        
        limitedHistory.forEach { host ->
            val jsonObject = JSONObject().apply {
                put("id", host.id)
                put("hostname", host.hostname)
                put("lastUsed", dateFormat.format(host.lastUsed))
                put("useCount", host.useCount)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_HOST_HISTORY, jsonArray.toString()).apply()
    }
    
    suspend fun loadHostHistory(): List<HostHistory> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_HOST_HISTORY, null) ?: return@withContext emptyList()
        
        try {
            val jsonArray = JSONArray(jsonString)
            val history = mutableListOf<HostHistory>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val lastUsed = try {
                    dateFormat.parse(jsonObject.getString("lastUsed")) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
                
                history.add(
                    HostHistory(
                        id = jsonObject.getString("id"),
                        hostname = jsonObject.getString("hostname"),
                        lastUsed = lastUsed,
                        useCount = jsonObject.getInt("useCount")
                    )
                )
            }
            
            history
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_SCAN_HISTORY)
            .remove(KEY_HOST_HISTORY)
            .apply()
    }
    
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    fun getCacheSize(): Long {
        // Calculate actual cache size in MB
        // This is a placeholder - you might want to implement actual cache size calculation
        return 0L
    }
    
    fun clearCache() {
        // Clear app cache
        // This is a placeholder - implement actual cache clearing logic
    }
    
    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_DARK_MODE, false)
            .putBoolean(KEY_DYNAMIC_COLOR, true)
            .putBoolean(KEY_AUTO_CLEAR_HISTORY, false)
            .putInt(KEY_HISTORY_LIMIT, DEFAULT_HISTORY_LIMIT)
            .putLong(KEY_SCAN_TIMEOUT, DEFAULT_SCAN_TIMEOUT)
            .putInt(KEY_CONCURRENT_THREADS, DEFAULT_CONCURRENT_THREADS)
            .putBoolean(KEY_ENABLE_NOTIFICATIONS, true)
            .putInt(KEY_CACHE_SIZE_LIMIT, DEFAULT_CACHE_SIZE_LIMIT)
            .apply()
    }
    
    // Register preference change listener
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    
    // Unregister preference change listener
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}