package com.ccdyz.tools.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用设置状态
 */
data class AppSettings(
    val isDarkMode: Boolean = false,
    val isDynamicColorEnabled: Boolean = true,
    val isAutoClearHistoryEnabled: Boolean = false,
    val historyLimit: Int = AppPreferencesManager.DEFAULT_HISTORY_LIMIT,
    val scanTimeout: Long = AppPreferencesManager.DEFAULT_SCAN_TIMEOUT,
    val concurrentThreads: Int = AppPreferencesManager.DEFAULT_CONCURRENT_THREADS,
    val isNotificationsEnabled: Boolean = true,
    val cacheSizeLimit: Int = AppPreferencesManager.DEFAULT_CACHE_SIZE_LIMIT
)

/**
 * 全局设置管理器ViewModel
 */
class AppSettingsManager(context: Context) : ViewModel() {
    
    private val preferencesManager = AppPreferencesManager(context)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        return AppSettings(
            isDarkMode = preferencesManager.isDarkMode,
            isDynamicColorEnabled = preferencesManager.isDynamicColorEnabled,
            isAutoClearHistoryEnabled = preferencesManager.isAutoClearHistoryEnabled,
            historyLimit = preferencesManager.historyLimit,
            scanTimeout = preferencesManager.scanTimeout,
            concurrentThreads = preferencesManager.concurrentThreads,
            isNotificationsEnabled = preferencesManager.isNotificationsEnabled,
            cacheSizeLimit = preferencesManager.cacheSizeLimit
        )
    }
    
    fun updateDarkMode(enabled: Boolean) {
        preferencesManager.isDarkMode = enabled
        _settings.update { it.copy(isDarkMode = enabled) }
    }
    
    fun updateDynamicColor(enabled: Boolean) {
        preferencesManager.isDynamicColorEnabled = enabled
        _settings.update { it.copy(isDynamicColorEnabled = enabled) }
    }
    
    fun updateAutoClearHistory(enabled: Boolean) {
        preferencesManager.isAutoClearHistoryEnabled = enabled
        _settings.update { it.copy(isAutoClearHistoryEnabled = enabled) }
    }
    
    fun updateHistoryLimit(limit: Int) {
        if (limit in 10..200) {
            preferencesManager.historyLimit = limit
            _settings.update { it.copy(historyLimit = limit) }
        }
    }
    
    fun updateScanTimeout(timeout: Long) {
        if (timeout in 1000..10000) {
            preferencesManager.scanTimeout = timeout
            _settings.update { it.copy(scanTimeout = timeout) }
        }
    }
    
    fun updateConcurrentThreads(threads: Int) {
        if (threads in 10..500) {
            preferencesManager.concurrentThreads = threads
            _settings.update { it.copy(concurrentThreads = threads) }
        }
    }
    
    fun updateNotifications(enabled: Boolean) {
        preferencesManager.isNotificationsEnabled = enabled
        _settings.update { it.copy(isNotificationsEnabled = enabled) }
    }
    
    fun updateCacheSizeLimit(limit: Int) {
        if (limit in 50..1000) {
            preferencesManager.cacheSizeLimit = limit
            _settings.update { it.copy(cacheSizeLimit = limit) }
        }
    }
    
    fun resetToDefaults() {
        preferencesManager.resetToDefaults()
        _settings.value = loadSettings()
    }
}

/**
 * 工厂类，用于创建AppSettingsManager实例
 */
class AppSettingsManagerFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSettingsManager::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSettingsManager(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Composable函数，用于获取AppSettingsManager实例
 */
@Composable
fun rememberAppSettingsManager(): AppSettingsManager {
    val context = AppContext.get()
    val factory = remember(context) { AppSettingsManagerFactory(context) }
    return viewModel(factory = factory)
}