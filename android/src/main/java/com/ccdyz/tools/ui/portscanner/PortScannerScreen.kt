package com.ccdyz.tools.ui.portscanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ccdyz.tools.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScannerScreen(
    onBackClick: () -> Unit,
    viewModel: PortScannerViewModel = viewModel()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 顶部栏
            TopAppBar(
                title = { Text("TCP端口扫描器", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 扫描输入表单
                item {
                    ScanInputCard(
                        uiState = uiState,
                        onHostnameChange = viewModel::updateHostname,
                        onStartPortChange = viewModel::updateStartPort,
                        onEndPortChange = viewModel::updateEndPort,
                        onTimeoutChange = viewModel::updateTimeout,
                        onThreadCountChange = viewModel::updateThreadCount,
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onSelectHostSuggestion = viewModel::selectHostSuggestion,
                        onHideHostSuggestions = viewModel::hideHostSuggestions
                    )
                }

                // 扫描进度
                if (uiState.isScanning) {
                    item {
                        ScanProgressCard(uiState = uiState)
                    }
                }

                // 扫描结果
                if (uiState.openPorts.isNotEmpty()) {
                    item {
                        ScanResultCard(openPorts = uiState.openPorts)
                    }
                }

                // 错误信息
                uiState.errorMessage?.let { error ->
                    item {
                        ErrorCard(errorMessage = error)
                    }
                }

                // 扫描历史
                if (uiState.scanHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = "扫描历史",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(uiState.scanHistory) { history ->
                        HistoryCard(
                            history = history,
                            onDelete = { viewModel.deleteHistory(history) },
                            onReuse = { 
                                viewModel.updateHostname(history.hostname)
                                val portRange = history.portRange.split("-")
                                if (portRange.size == 2) {
                                    viewModel.updateStartPort(portRange[0])
                                    viewModel.updateEndPort(portRange[1])
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanInputCard(
    uiState: PortScannerUiState,
    onHostnameChange: (String) -> Unit,
    onStartPortChange: (String) -> Unit,
    onEndPortChange: (String) -> Unit,
    onTimeoutChange: (String) -> Unit,
    onThreadCountChange: (String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectHostSuggestion: (String) -> Unit,
    onHideHostSuggestions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "扫描配置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 主机名输入
            Box {
                OutlinedTextField(
                    value = uiState.hostname,
                    onValueChange = onHostnameChange,
                    label = { Text("主机名或IP地址") },
                    placeholder = { Text("例如: google.com 或 192.168.1.1") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isScanning,
                    trailingIcon = if (uiState.showHostSuggestions && uiState.hostSuggestions.isNotEmpty()) {
                        {
                            IconButton(onClick = onHideHostSuggestions) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "隐藏建议")
                            }
                        }
                    } else if (uiState.hostSuggestions.isNotEmpty()) {
                        {
                            IconButton(onClick = { onHostnameChange(uiState.hostname) }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "显示建议")
                            }
                        }
                    } else null
                )
                
                // 主机名建议下拉列表
                if (uiState.showHostSuggestions && uiState.hostSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 60.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(uiState.hostSuggestions) { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectHostSuggestion(suggestion) }
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 端口范围
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.startPort,
                    onValueChange = onStartPortChange,
                    label = { Text("起始端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isScanning
                )
                OutlinedTextField(
                    value = uiState.endPort,
                    onValueChange = onEndPortChange,
                    label = { Text("结束端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isScanning
                )
            }

            // 高级设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.timeout,
                    onValueChange = onTimeoutChange,
                    label = { Text("超时(ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isScanning
                )
                OutlinedTextField(
                    value = uiState.threadCount,
                    onValueChange = onThreadCountChange,
                    label = { Text("并发数") },
                    placeholder = { Text("建议: 50-200") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isScanning,
                    supportingText = { 
                        Text(
                            "控制同时扫描的端口数量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                )
            }

            // 扫描按钮
            if (uiState.isScanning) {
                Button(
                    onClick = onStopScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("停止扫描")
                }
            } else {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.hostname.isNotBlank()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始扫描")
                }
            }
        }
    }
}

@Composable
private fun ScanProgressCard(uiState: PortScannerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "扫描进度",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LinearProgressIndicator(
                progress = uiState.progress,
                modifier = Modifier.fillMaxWidth()
            )

            // 基本进度信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("进度: ${(uiState.progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
                Text("${uiState.scannedPorts}/${uiState.totalPorts}", color = MaterialTheme.colorScheme.onSurface)
            }

            // 性能统计信息
            if (uiState.scanSpeed > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "扫描速度: ${String.format("%.1f", uiState.scanSpeed)} 端口/秒",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    if (uiState.estimatedTimeRemaining > 0) {
                        Text(
                            text = "预计剩余: ${formatTime(uiState.estimatedTimeRemaining)}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 开放端口统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("已发现 ${uiState.openPorts.size} 个开放端口", color = MaterialTheme.colorScheme.onSurface)
                if (uiState.openPorts.isNotEmpty()) {
                    Text(
                        text = "开放率: ${String.format("%.2f", (uiState.openPorts.size.toFloat() / uiState.scannedPorts * 100))}%",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanResultCard(openPorts: List<Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✅ 发现 ${openPorts.size} 个开放端口",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green.copy(red = 0.2f)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(openPorts) { port ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("端口 $port", color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = getPortServiceName(port) ?: "未知服务",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(errorMessage: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "❌ 错误",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red.copy(green = 0.2f, blue = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HistoryCard(
    history: com.ccdyz.tools.data.database.entities.ScanHistory,
    onDelete: () -> Unit,
    onReuse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = history.hostname,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "端口: ${history.portRange}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "开放: ${history.openPortsCount}/${history.totalPorts}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(history.scanTime),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Row {
                    IconButton(onClick = onReuse) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重用配置",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (history.openPorts.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开放端口: ${history.openPorts}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun getPortServiceName(port: Int): String? {
    return when (port) {
        21 -> "FTP"
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        80 -> "HTTP"
        110 -> "POP3"
        143 -> "IMAP"
        443 -> "HTTPS"
        993 -> "IMAPS"
        995 -> "POP3S"
        3389 -> "RDP"
        5432 -> "PostgreSQL"
        3306 -> "MySQL"
        1433 -> "SQL Server"
        6379 -> "Redis"
        27017 -> "MongoDB"
        8080 -> "HTTP-Alt"
        8443 -> "HTTPS-Alt"
        else -> null
    }
}

/**
 * 格式化时间显示
 * @param milliseconds 毫秒数
 * @return 格式化的时间字符串
 */
private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
    }
}