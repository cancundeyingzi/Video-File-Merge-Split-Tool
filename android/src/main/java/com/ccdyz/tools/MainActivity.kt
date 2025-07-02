package com.ccdyz.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ccdyz.tools.ui.theme.AppTheme
import com.ccdyz.tools.ui.theme.rememberThemeSettings
import com.ccdyz.tools.ui.navigation.ToolsAppNavigation
import com.ccdyz.tools.utils.AppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize app context
        AppContext.init(this)

        setContent {
            // Get theme settings
            val themeSettings = rememberThemeSettings()
            
            // Apply theme
            AppTheme(themeSettings = themeSettings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ToolsAppNavigation()
                }
            }
        }
    }
}