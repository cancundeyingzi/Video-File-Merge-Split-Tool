// app/src/main/java/com/ccdyz/tools/ui/filemerger/FileMergerViewModel.kt
package com.ccdyz.tools.ui.filemerger

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ccdyz.tools.utils.FileUtils
import com.ccdyz.tools.utils.MergedFileProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.pm.PackageManager

enum class FileMergerTab { MERGE, SPLIT }
enum class ProcessingMode { MEMORY, STREAM }

data class FileInfo(
    val name: String,
    val size: Long,
    val mimeType: String?,
    val uri: Uri
)

data class OperationResult(
    val success: Boolean,
    val message: String,
    val outputFiles: List<OutputFile> = emptyList(),
    val errorDetails: String? = null
)

data class OutputFile(
    val name: String,
    val size: Long,
    val uri: Uri,
    val description: String,
    val folderPath: String? = null // 添加文件夹路径
)

data class FileMergerUiState(
    val currentTab: FileMergerTab = FileMergerTab.MERGE,
    val developerMode: Boolean = false,

    // 文件选择
    val videoFile: FileInfo? = null,
    val attachFile: FileInfo? = null,
    val mergedFile: FileInfo? = null,

    // 处理设置
    val processingMode: ProcessingMode = ProcessingMode.MEMORY,

    // 处理状态
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val currentOperation: String = "",
    val processedSize: Long = 0L,
    val totalSize: Long = 0L,

    // 结果
    val operationResult: OperationResult? = null,

    // 调试信息
    val debugInfo: String = ""
)

class FileMergerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FileMergerUiState())
    val uiState: StateFlow<FileMergerUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    private val processor = MergedFileProcessor()

    fun switchTab(tab: FileMergerTab) {
        _uiState.update { it.copy(currentTab = tab) }
        clearOperationResult()
    }

    fun toggleDeveloperMode() {
        val newMode = !_uiState.value.developerMode
        _uiState.update {
            it.copy(
                developerMode = newMode,
                debugInfo = if (newMode) {
                    buildString {
                        appendLine("🔧 开发者调试模式已启用")
                        appendLine("📱 应用版本: 1.0.0")
                        appendLine("🔧 MERGEDv3处理器版本: 3.0")
                        appendLine("💾 支持的处理模式: 内存模式, 流式模式")
                        appendLine("🎯 魔术字节: MERGEDv3 (4D 45 52 47 45 44 76 33)")
                        appendLine("📊 头部结构: 视频数据 + 附件数据 + 元数据 + 魔术字节(网页版兼容)")
                        appendLine("⏰ ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                        appendLine("=".repeat(50))
                    }
                } else ""
            )
        }
        addDebugInfo("🔧 开发模式: ${if (newMode) "✅ 启用" else "❌ 关闭"}")
    }

    fun selectVideoFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                addDebugInfo("🎬 开始选择视频文件...")
                addDebugInfo("📍 URI: $uri")

                val fileInfo = FileUtils.getFileInfo(context, uri)
                _uiState.update { it.copy(videoFile = fileInfo) }

                addDebugInfo("✅ 视频文件选择成功:")
                addDebugInfo("  📁 文件名: ${fileInfo.name}")
                addDebugInfo("  📊 文件大小: ${formatBytes(fileInfo.size)}")
                addDebugInfo("  🏷️ MIME类型: ${fileInfo.mimeType ?: "未知"}")
                addDebugInfo("  📍 URI: ${fileInfo.uri}")

                updateProcessingMode()
            } catch (e: Exception) {
                val errorDetails = buildString {
                    appendLine("❌ 视频文件选择失败")
                    appendLine("📍 URI: $uri")
                    appendLine("🐛 错误类型: ${e.javaClass.simpleName}")
                    appendLine("💬 错误消息: ${e.message}")
                    if (e.cause != null) {
                        appendLine("🔗 根本原因: ${e.cause?.message}")
                    }
                }
                addDebugInfo(errorDetails)
            }
        }
    }

    fun selectAttachFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                addDebugInfo("📎 开始选择附件文件...")
                addDebugInfo("📍 URI: $uri")

                val fileInfo = FileUtils.getFileInfo(context, uri)
                _uiState.update { it.copy(attachFile = fileInfo) }

                addDebugInfo("✅ 附件文件选择成功:")
                addDebugInfo("  📁 文件名: ${fileInfo.name}")
                addDebugInfo("  📊 文件大小: ${formatBytes(fileInfo.size)}")
                addDebugInfo("  🏷️ MIME类型: ${fileInfo.mimeType ?: "未知"}")
                addDebugInfo("  📍 URI: ${fileInfo.uri}")

                updateProcessingMode()
            } catch (e: Exception) {
                val errorDetails = buildString {
                    appendLine("❌ 附件文件选择失败")
                    appendLine("📍 URI: $uri")
                    appendLine("🐛 错误类型: ${e.javaClass.simpleName}")
                    appendLine("💬 错误消息: ${e.message}")
                    if (e.cause != null) {
                        appendLine("🔗 根本原因: ${e.cause?.message}")
                    }
                }
                addDebugInfo(errorDetails)
            }
        }
    }

    fun selectMergedFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                addDebugInfo("🔧 开始选择合并文件...")
                addDebugInfo("📍 URI: $uri")

                val fileInfo = FileUtils.getFileInfo(context, uri)
                _uiState.update { it.copy(mergedFile = fileInfo) }

                addDebugInfo("✅ 合并文件选择成功:")
                addDebugInfo("  📁 文件名: ${fileInfo.name}")
                addDebugInfo("  📊 文件大小: ${formatBytes(fileInfo.size)}")
                addDebugInfo("  🏷️ MIME类型: ${fileInfo.mimeType ?: "未知"}")
                addDebugInfo("  📍 URI: ${fileInfo.uri}")

                // 检测是否为MERGEDv3格式
                addDebugInfo("🔍 开始格式检测...")
                val isV3Format = processor.detectMergedFormat(context, uri, ::addDebugInfo)
                addDebugInfo("🎯 格式检测结果: ${if (isV3Format) "✅ 有效的MERGEDv3文件" else "❌ 不是MERGEDv3格式"}")
            } catch (e: Exception) {
                val errorDetails = buildString {
                    appendLine("❌ 合并文件选择失败")
                    appendLine("📍 URI: $uri")
                    appendLine("🐛 错误类型: ${e.javaClass.simpleName}")
                    appendLine("💬 错误消息: ${e.message}")
                    if (e.cause != null) {
                        appendLine("🔗 根本原因: ${e.cause?.message}")
                    }
                }
                addDebugInfo(errorDetails)
            }
        }
    }

    fun selectProcessingMode(mode: ProcessingMode) {
        _uiState.update { it.copy(processingMode = mode) }
        addDebugInfo("💾 手动选择处理模式: ${mode.name}")
        addDebugInfo("  📝 模式说明: ${when(mode) {
            ProcessingMode.MEMORY -> "内存模式 - 适合小文件，速度快但占用内存多"
            ProcessingMode.STREAM -> "流式模式 - 适合大文件，节省内存但速度稍慢"
        }}")
    }

    fun startMerge(context: Context) {
        val state = _uiState.value
        val videoFile = state.videoFile ?: return
        val attachFile = state.attachFile ?: return

        processingJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isProcessing = true,
                        progress = 0f,
                        currentOperation = "准备合并...",
                        totalSize = videoFile.size + attachFile.size,
                        operationResult = null
                    )
                }

                addDebugInfo("🚀 开始文件合并操作")
                addDebugInfo("  💾 处理模式: ${state.processingMode.name}")
                addDebugInfo("  🎬 视频文件: ${videoFile.name} (${formatBytes(videoFile.size)})")
                addDebugInfo("  📎 附件文件: ${attachFile.name} (${formatBytes(attachFile.size)})")
                addDebugInfo("  📊 预计输出大小: ${formatBytes(videoFile.size + attachFile.size + 32)} (含元数据)")

                val result = when (state.processingMode) {
                    ProcessingMode.MEMORY -> processor.mergeFilesMemory(
                        context = context,
                        videoUri = videoFile.uri,
                        attachUri = attachFile.uri,
                        onProgress = { progress, operation, processedSize ->
                            _uiState.update {
                                it.copy(
                                    progress = progress,
                                    currentOperation = operation,
                                    processedSize = processedSize
                                )
                            }
                        },
                        onDebug = ::addDebugInfo
                    )
                    ProcessingMode.STREAM -> processor.mergeFilesStream(
                        context = context,
                        videoUri = videoFile.uri,
                        attachUri = attachFile.uri,
                        onProgress = { progress, operation, processedSize ->
                            _uiState.update {
                                it.copy(
                                    progress = progress,
                                    currentOperation = operation,
                                    processedSize = processedSize
                                )
                            }
                        },
                        onDebug = ::addDebugInfo
                    )
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationResult = result
                    )
                }

                addDebugInfo("🎉 合并操作完成: ${if (result.success) "✅ 成功" else "❌ 失败"}")
                if (result.success) {
                    // 修复：明确指定类型以避免歧义
                    result.outputFiles.forEach { file: OutputFile ->
                        addDebugInfo("  📄 输出文件: ${file.name} (${formatBytes(file.size)})")
                        addDebugInfo("  📍 文件路径: ${file.uri}")
                    }
                } else {
                    addDebugInfo("  💬 失败原因: ${result.message}")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationResult = OperationResult(
                            success = false,
                            message = "合并失败: ${e.message}",
                            errorDetails = e.stackTraceToString()
                        )
                    )
                }
                addDebugInfo("合并异常: ${e.message}")
            }
        }
    }

    fun startSplit(context: Context) {
        val state = _uiState.value
        val mergedFile = state.mergedFile ?: return

        processingJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isProcessing = true,
                        progress = 0f,
                        currentOperation = "准备拆分...",
                        totalSize = mergedFile.size,
                        operationResult = null
                    )
                }

                addDebugInfo("🔧 开始文件拆分操作")
                addDebugInfo("  📁 合并文件: ${mergedFile.name} (${formatBytes(mergedFile.size)})")
                addDebugInfo("  📍 文件路径: ${mergedFile.uri}")

                val result = processor.splitMergedFile(
                    context = context,
                    mergedUri = mergedFile.uri,
                    onProgress = { progress, operation, processedSize ->
                        _uiState.update {
                            it.copy(
                                progress = progress,
                                currentOperation = operation,
                                processedSize = processedSize
                            )
                        }
                    },
                    onDebug = ::addDebugInfo
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationResult = result
                    )
                }

                addDebugInfo("🎉 拆分操作完成: ${if (result.success) "✅ 成功" else "❌ 失败"}")
                if (result.success) {
                    // 修复：明确指定类型以避免歧义
                    result.outputFiles.forEach { file: OutputFile ->
                        addDebugInfo("  📄 输出文件: ${file.name} (${formatBytes(file.size)})")
                        addDebugInfo("  📝 文件描述: ${file.description}")
                        addDebugInfo("  📍 文件路径: ${file.uri}")
                    }
                } else {
                    addDebugInfo("  💬 失败原因: ${result.message}")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        operationResult = OperationResult(
                            success = false,
                            message = "拆分失败: ${e.message}",
                            errorDetails = e.stackTraceToString()
                        )
                    )
                }
                addDebugInfo("拆分异常: ${e.message}")
            }
        }
    }

    fun stopOperation() {
        processingJob?.cancel()
        _uiState.update {
            it.copy(
                isProcessing = false,
                currentOperation = "已取消"
            )
        }
        addDebugInfo("用户取消操作")
    }


    private fun updateProcessingMode() {
        val state = _uiState.value
        val videoSize = state.videoFile?.size ?: 0
        val attachSize = state.attachFile?.size ?: 0
        val totalSize = videoSize + attachSize

        addDebugInfo("🔄 自动处理模式评估:")
        addDebugInfo("  🎬 视频文件大小: ${formatBytes(videoSize)}")
        addDebugInfo("  📎 附件文件大小: ${formatBytes(attachSize)}")
        addDebugInfo("  📊 总大小: ${formatBytes(totalSize)}")

        // 自动选择处理模式
        val threshold = 1024 * 1024 * 1024L // 1GB
        if (totalSize > threshold) {
            _uiState.update { it.copy(processingMode = ProcessingMode.STREAM) }
            addDebugInfo("  💾 自动选择: 流式模式 (文件大小 > 1GB)")
            addDebugInfo("  📝 原因: 大文件使用流式模式可以节省内存")
        } else {
            addDebugInfo("  💾 当前模式: ${_uiState.value.processingMode.name}")
            addDebugInfo("  📝 建议: 文件较小，内存模式和流式模式都适用")
        }
    }

    private fun clearOperationResult() {
        _uiState.update { it.copy(operationResult = null) }
    }

    private fun addDebugInfo(info: String) {
        if (!_uiState.value.developerMode) return

        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val newInfo = "[$timestamp] $info"

        _uiState.update {
            it.copy(
                debugInfo = if (it.debugInfo.isBlank()) newInfo
                else "${it.debugInfo}\n$newInfo"
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
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
}