package com.ccdyz.tools.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ccdyz.tools.ui.home.HomeScreen
import com.ccdyz.tools.ui.portscanner.PortScannerScreen
import com.ccdyz.tools.ui.filemerger.FileMergerScreen
import com.ccdyz.tools.ui.settings.SettingsScreen

@Composable
fun ToolsAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onPortScannerClick = { navController.navigate("port_scanner") },
                onFileMergerClick = { navController.navigate("file_merger") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }

        composable("port_scanner") {
            PortScannerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("file_merger") {
            FileMergerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}