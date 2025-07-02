package com.ccdyz.tools.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ccdyz.tools.ui.theme.BackgroundGradientEnd
import com.ccdyz.tools.ui.theme.BackgroundGradientStart
import com.ccdyz.tools.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = SettingsViewModel.create()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                SettingsHeader(onBackClick = onBackClick)
            }
            
            // Theme Settings
            item {
                SettingsSection(
                    title = "主题设置",
                    icon = Icons.Default.Palette
                ) {
                    SettingsSwitchItem(
                        title = "深色模式",
                        description = "启用深色主题 (立即生效)",
                        checked = uiState.isDarkMode,
                        onCheckedChange = viewModel::updateDarkMode
                    )
                    
                    SettingsSwitchItem(
                        title = "动态颜色",
                        description = "使用系统动态颜色（Android 12+，立即生效）",
                        checked = uiState.isDynamicColorEnabled,
                        onCheckedChange = viewModel::updateDynamicColor
                    )
                }
            }
            
            
            // History Settings
            item {
                SettingsSection(
                    title = "历史记录",
                    icon = Icons.Default.History
                ) {
                    SettingsSwitchItem(
                        title = "自动清理历史",
                        description = "达到限制时自动删除旧记录",
                        checked = uiState.isAutoClearHistoryEnabled,
                        onCheckedChange = viewModel::updateAutoClearHistory
                    )
                    
                    SettingsSliderItem(
                        title = "历史记录限制",
                        description = "最多保存的历史记录数量",
                        value = uiState.historyLimit.toFloat(),
                        valueRange = 10f..200f,
                        steps = 18,
                        onValueChange = { viewModel.updateHistoryLimit(it.toInt()) },
                        valueFormatter = { "${it.toInt()}条" }
                    )
                    
                    SettingsActionItem(
                        title = "清除历史记录",
                        description = "删除所有扫描和主机历史记录",
                        icon = Icons.Default.Delete,
                        onClick = viewModel::showClearHistoryDialog
                    )
                }
            }
            
            // Notification Settings
            item {
                SettingsSection(
                    title = "通知设置",
                    icon = Icons.Default.Notifications
                ) {
                    SettingsSwitchItem(
                        title = "启用通知",
                        description = "显示扫描完成和错误通知",
                        checked = uiState.isNotificationsEnabled,
                        onCheckedChange = viewModel::updateNotifications
                    )
                }
            }
            
            // Cache Settings
            item {
                SettingsSection(
                    title = "缓存管理",
                    icon = Icons.Default.Storage
                ) {
                    SettingsSliderItem(
                        title = "缓存大小限制",
                        description = "应用缓存的最大大小",
                        value = uiState.cacheSizeLimit.toFloat(),
                        valueRange = 50f..1000f,
                        steps = 18,
                        onValueChange = { viewModel.updateCacheSizeLimit(it.toInt()) },
                        valueFormatter = { "${it.toInt()}MB" }
                    )
                    
                    SettingsInfoItem(
                        title = "当前缓存大小",
                        description = "${uiState.currentCacheSize}MB"
                    )
                    
                    SettingsActionItem(
                        title = "清除缓存",
                        description = "删除所有临时文件和缓存",
                        icon = Icons.Default.CleaningServices,
                        onClick = viewModel::showClearCacheDialog
                    )
                }
            }
            
            // About Section
            item {
                SettingsSection(
                    title = "关于",
                    icon = Icons.Default.Info
                ) {
                    SettingsInfoItem(
                        title = "应用版本",
                        description = uiState.appVersion
                    )
                    
                    SettingsActionItem(
                        title = "恢复默认设置",
                        description = "重置所有设置为默认值",
                        icon = Icons.Default.RestartAlt,
                        onClick = viewModel::showResetDialog
                    )
                }
            }
        }
        
        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
    
    // Dialogs
    if (uiState.showResetDialog) {
        ConfirmationDialog(
            title = "恢复默认设置",
            message = "确定要将所有设置恢复为默认值吗？此操作不可撤销。",
            onConfirm = viewModel::resetToDefaults,
            onDismiss = viewModel::hideResetDialog
        )
    }
    
    if (uiState.showClearCacheDialog) {
        ConfirmationDialog(
            title = "清除缓存",
            message = "确定要清除所有缓存文件吗？这可能会影响应用性能。",
            onConfirm = viewModel::clearCache,
            onDismiss = viewModel::hideClearCacheDialog
        )
    }
    
    if (uiState.showClearHistoryDialog) {
        ConfirmationDialog(
            title = "清除历史记录",
            message = "确定要删除所有历史记录吗？此操作不可撤销。",
            onConfirm = viewModel::clearHistory,
            onDismiss = viewModel::hideClearHistoryDialog
        )
    }
}

@Composable
fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "⚙️ 设置",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            content()
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryBlue,
                checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Text(
                text = valueFormatter(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryBlue
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryBlue,
                activeTrackColor = PrimaryBlue,
                inactiveTrackColor = PrimaryBlue.copy(alpha = 0.3f)
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "执行",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsInfoItem(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Text(
            text = description,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryBlue,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "确定",
                    color = PrimaryBlue
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}