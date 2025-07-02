package com.ccdyz.tools.ui.theme

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ccdyz.tools.utils.AppPreferencesManager
import kotlinx.coroutines.delay

@Composable
fun rememberThemeSettings(): ThemeSettings {
    val context = LocalContext.current
    val preferencesManager = remember { AppPreferencesManager(context) }
    val systemInDarkTheme = isSystemInDarkTheme()
    
    var isDarkMode by remember { mutableStateOf(preferencesManager.isDarkMode) }
    var isDynamicColorEnabled by remember { mutableStateOf(preferencesManager.isDynamicColorEnabled) }
    
    // Listen for preference changes more frequently
    LaunchedEffect(Unit) {
        while(true) {
            isDarkMode = preferencesManager.isDarkMode
            isDynamicColorEnabled = preferencesManager.isDynamicColorEnabled
            delay(100) // Check every 100ms
        }
    }
    
    // Add preference change listener
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "dark_mode" -> isDarkMode = preferencesManager.isDarkMode
                "dynamic_color" -> isDynamicColorEnabled = preferencesManager.isDynamicColorEnabled
            }
        }
        
        preferencesManager.registerListener(listener)
        
        onDispose {
            preferencesManager.unregisterListener(listener)
        }
    }
    
    return ThemeSettings(
        isDarkMode = isDarkMode,
        isDynamicColorEnabled = isDynamicColorEnabled,
        systemInDarkTheme = systemInDarkTheme
    )
}

data class ThemeSettings(
    val isDarkMode: Boolean,
    val isDynamicColorEnabled: Boolean,
    val systemInDarkTheme: Boolean
) {
    val effectiveDarkTheme: Boolean
        get() = isDarkMode || systemInDarkTheme
}

@Composable
fun AppTheme(
    themeSettings: ThemeSettings = rememberThemeSettings(),
    content: @Composable () -> Unit
) {
    ToolsAppTheme(
        darkTheme = themeSettings.effectiveDarkTheme,
        dynamicColor = themeSettings.isDynamicColorEnabled,
        content = content
    )
}