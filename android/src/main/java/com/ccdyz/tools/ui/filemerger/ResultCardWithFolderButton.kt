package com.ccdyz.tools.ui.filemerger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultCardWithFolderButton(
    result: OperationResult, 
    developerMode: Boolean = false,
    viewModel: FileMergerViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) 
                Color.Green.copy(alpha = 0.1f) 
            else 
                Color.Red.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (result.success) "Success" else "Failed",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                result.message,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (result.outputFiles.isNotEmpty()) {
                Text(
                    "Output Files:", 
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.outputFiles.forEach { file ->
                    Text(
                        "• ${file.name} (${formatFileSize(file.size)})",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (developerMode) {
                        Text(
                            text = "  URI: ${file.uri}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = "  Description: ${file.description}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Add tip about file location
                Text(
                    text = "文件已保存到下载目录 (Downloads)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Show detailed error info in developer mode
            if (developerMode && !result.success && result.errorDetails != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detailed Error Info:",
                    fontWeight = FontWeight.Medium,
                    color = Color.Red
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
                ) {
                    Text(
                        text = result.errorDetails,
                        modifier = Modifier.padding(8.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
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