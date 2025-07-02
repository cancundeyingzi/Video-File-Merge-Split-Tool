package com.ccdyz.tools.ui.filemerger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ccdyz.tools.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileMergerScreen(
    onBackClick: () -> Unit,
    viewModel: FileMergerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // File pickers
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectVideoFile(context, it) }
    }

    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectAttachFile(context, it) }
    }

    val mergedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectMergedFile(context, it) }
    }

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
            // Top bar
            TopAppBar(
                title = { Text("文件合并拆分器", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Developer mode toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔧", color = Color.White)
                        Switch(
                            checked = uiState.developerMode,
                            onCheckedChange = { viewModel.toggleDeveloperMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab row
            TabRow(
                selectedTabIndex = if (uiState.currentTab == FileMergerTab.MERGE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                contentColor = Color.White
            ) {
                Tab(
                    selected = uiState.currentTab == FileMergerTab.MERGE,
                    onClick = { viewModel.switchTab(FileMergerTab.MERGE) },
                    text = { Text("合并") }
                )
                Tab(
                    selected = uiState.currentTab == FileMergerTab.SPLIT,
                    onClick = { viewModel.switchTab(FileMergerTab.SPLIT) },
                    text = { Text("拆分") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content area
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (uiState.currentTab) {
                    FileMergerTab.MERGE -> {
                        item {
                            MergeTabContent(
                                uiState = uiState,
                                onSelectVideo = { videoLauncher.launch("video/*") },
                                onSelectAttach = { attachLauncher.launch("*/*") },
                                onStartMerge = { viewModel.startMerge(context) },
                                onSelectMode = viewModel::selectProcessingMode
                            )
                        }
                    }
                    FileMergerTab.SPLIT -> {
                        item {
                            SplitTabContent(
                                uiState = uiState,
                                onSelectMerged = { mergedLauncher.launch("*/*") },
                                onStartSplit = { viewModel.startSplit(context) }
                            )
                        }
                    }
                }

                // Processing progress
                if (uiState.isProcessing) {
                    item {
                        ProcessingCard(uiState = uiState, onStop = viewModel::stopOperation)
                    }
                }

                // Result display with folder button
                uiState.operationResult?.let { result ->
                    item {
                        ResultCardWithFolderButton(
                            result = result, 
                            developerMode = uiState.developerMode,
                            viewModel = viewModel
                        )
                    }
                }

                // Developer debug info
                if (uiState.developerMode && uiState.debugInfo.isNotBlank()) {
                    item {
                        DebugCard(debugInfo = uiState.debugInfo)
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeTabContent(
    uiState: FileMergerUiState,
    onSelectVideo: () -> Unit,
    onSelectAttach: () -> Unit,
    onStartMerge: () -> Unit,
    onSelectMode: (ProcessingMode) -> Unit
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
                text = "文件合并",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Video file selection
            FileSelectionCard(
                title = "视频文件",
                fileInfo = uiState.videoFile,
                onSelect = onSelectVideo,
                icon = Icons.Default.PlayArrow
            )

            // Attachment file selection
            FileSelectionCard(
                title = "附件文件",
                fileInfo = uiState.attachFile,
                onSelect = onSelectAttach,
                icon = Icons.Default.Attachment
            )

            // Processing mode selection
            Text(
                "处理模式:", 
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { onSelectMode(ProcessingMode.MEMORY) },
                    label = { Text("内存模式") },
                    selected = uiState.processingMode == ProcessingMode.MEMORY
                )
                FilterChip(
                    onClick = { onSelectMode(ProcessingMode.STREAM) },
                    label = { Text("流式模式") },
                    selected = uiState.processingMode == ProcessingMode.STREAM
                )
            }

            // Start merge button
            Button(
                onClick = onStartMerge,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.videoFile != null && uiState.attachFile != null && !uiState.isProcessing
            ) {
                Text("开始合并")
            }
        }
    }
}

@Composable
private fun SplitTabContent(
    uiState: FileMergerUiState,
    onSelectMerged: () -> Unit,
    onStartSplit: () -> Unit
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
                text = "文件拆分",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Merged file selection
            FileSelectionCard(
                title = "MERGEDv3文件",
                fileInfo = uiState.mergedFile,
                onSelect = onSelectMerged,
                icon = Icons.Default.Build
            )

            // Start split button
            Button(
                onClick = onStartSplit,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.mergedFile != null && !uiState.isProcessing
            ) {
                Text("开始拆分")
            }
        }
    }
}

@Composable
private fun FileSelectionCard(
    title: String,
    fileInfo: FileInfo?,
    onSelect: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        title, 
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onSelect) {
                    Text("选择文件")
                }
            }

            if (fileInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = fileInfo.name,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatFileSize(fileInfo.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ProcessingCard(
    uiState: FileMergerUiState,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "处理中...", 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onStop) {
                    Text("取消")
                }
            }

            Text(
                uiState.currentOperation,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            LinearProgressIndicator(
                progress = uiState.progress,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                "${(uiState.progress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DebugCard(debugInfo: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔧 调试信息",
                    color = Color.Green,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "实时日志",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = debugInfo,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        kb >= 1 -> "%.2f KB".format(kb)
        else -> "$bytes B"
    }
}