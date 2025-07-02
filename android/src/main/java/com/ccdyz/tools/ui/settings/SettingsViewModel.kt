package com.ccdyz.tools.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ccdyz.tools.utils.AppContext
import com.ccdyz.tools.utils.AppPreferencesManager
import com.ccdyz.tools.utils.AppSettingsManager
import com.ccdyz.tools.utils.rememberAppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val isDynamicColorEnabled: Boolean = true,
    val isAutoClearHistoryEnabled: Boolean = false,
    val historyLimit: Int = AppPreferencesManager.DEFAULT_HISTORY_LIMIT,
    val isNotificationsEnabled: Boolean = true,
    val cacheSizeLimit: Int = AppPreferencesManager.DEFAULT_CACHE_SIZE_LIMIT,
    val currentCacheSize: Long = 0L,
    val appVersion: String = "1.0.0",
    val isLoading: Boolean = false,
    val showResetDialog: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val showClearHistoryDialog: Boolean = false
)

class SettingsViewModel(
    private val appSettingsManager: AppSettingsManager
) : ViewModel() {
    
    private val preferencesManager = AppPreferencesManager(AppContext.get())
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    companion object {
        @Composable
        fun create(): SettingsViewModel {
            val appSettingsManager = rememberAppSettingsManager()
            return viewModel { SettingsViewModel(appSettingsManager) }
        }
    }
    
    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            isDarkMode = preferencesManager.isDarkMode,
            isDynamicColorEnabled = preferencesManager.isDynamicColorEnabled,
            isAutoClearHistoryEnabled = preferencesManager.isAutoClearHistoryEnabled,
            historyLimit = preferencesManager.historyLimit,
            isNotificationsEnabled = preferencesManager.isNotificationsEnabled,
            cacheSizeLimit = preferencesManager.cacheSizeLimit,
            currentCacheSize = preferencesManager.getCacheSize(),
            appVersion = preferencesManager.appVersion
        )
    }
    
    fun updateDarkMode(enabled: Boolean) {
        appSettingsManager.updateDarkMode(enabled)
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }
    
    fun updateDynamicColor(enabled: Boolean) {
        appSettingsManager.updateDynamicColor(enabled)
        _uiState.value = _uiState.value.copy(isDynamicColorEnabled = enabled)
    }
    
    fun updateAutoClearHistory(enabled: Boolean) {
        appSettingsManager.updateAutoClearHistory(enabled)
        _uiState.value = _uiState.value.copy(isAutoClearHistoryEnabled = enabled)
    }
    
    fun updateHistoryLimit(limit: Int) {
        appSettingsManager.updateHistoryLimit(limit)
        _uiState.value = _uiState.value.copy(historyLimit = limit)
    }
    
    
    fun updateNotifications(enabled: Boolean) {
        appSettingsManager.updateNotifications(enabled)
        _uiState.value = _uiState.value.copy(isNotificationsEnabled = enabled)
    }
    
    fun updateCacheSizeLimit(limit: Int) {
        appSettingsManager.updateCacheSizeLimit(limit)
        _uiState.value = _uiState.value.copy(cacheSizeLimit = limit)
    }
    
    fun showResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = true)
    }
    
    fun hideResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false)
    }
    
    fun showClearCacheDialog() {
        _uiState.value = _uiState.value.copy(showClearCacheDialog = true)
    }
    
    fun hideClearCacheDialog() {
        _uiState.value = _uiState.value.copy(showClearCacheDialog = false)
    }
    
    fun showClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = true)
    }
    
    fun hideClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
    }
    
    fun resetToDefaults() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                appSettingsManager.resetToDefaults()
                loadSettings()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, showResetDialog = false)
            }
        }
    }
    
    fun clearCache() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                preferencesManager.clearCache()
                _uiState.value = _uiState.value.copy(currentCacheSize = 0L)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, showClearCacheDialog = false)
            }
        }
    }
    
    fun clearHistory() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                preferencesManager.clearAllHistory()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, showClearHistoryDialog = false)
            }
        }
    }
}