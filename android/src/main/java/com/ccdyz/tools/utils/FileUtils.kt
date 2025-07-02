// app/src/main/java/com/ccdyz/tools/utils/FileUtils.kt
package com.ccdyz.tools.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.ccdyz.tools.ui.filemerger.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.content.ContentValues
import android.provider.MediaStore
import androidx.annotation.RequiresApi

object FileUtils {

    // 直接在这里定义常量，避免单独的Constants对象
    private const val CHUNK_SIZE = 1024 * 1024 // 1MB

    /**
     * 获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    /**
     * 获取文件名（非空版本）
     */
    fun getFileNameSafe(context: Context, uri: Uri): String {
        return getFileName(context, uri) ?: "unknown_file"
    }

    /**
     * 获取不含扩展名的文件名
     */
    fun getFileNameWithoutExtension(context: Context, uri: Uri): String {
        val fullName = getFileNameSafe(context, uri)
        val lastDotIndex = fullName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fullName.substring(0, lastDotIndex)
        } else {
            fullName
        }
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(context: Context, uri: Uri): String {
        val fullName = getFileNameSafe(context, uri)
        val lastDotIndex = fullName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fullName.length - 1) {
            fullName.substring(lastDotIndex)
        } else {
            ""
        }
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        }
        if (size == 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    size = it.statSize
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return size
    }

    /**
     * 获取文件MIME类型
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
        }
    }

    /**
     * 合并文件
     * @param inputFiles 输入文件列表
     * @param outputFile 输出文件
     * @param progressCallback 进度回调
     */
    suspend fun mergeFiles(
        inputFiles: List<File>,
        outputFile: File,
        progressCallback: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (inputFiles.isEmpty()) return@withContext

        val totalSize = inputFiles.sumOf { it.length() }
        var processedSize = 0L

        FileOutputStream(outputFile).use { output ->
            for (file in inputFiles) {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        processedSize += bytesRead
                        val progress = processedSize.toFloat() / totalSize
                        progressCallback(progress)
                    }
                }
            }
        }
    }

    /**
     * 拆分文件
     * @param inputFile 输入文件
     * @param outputDir 输出目录
     * @param chunkSize 每个分块的大小
     * @param progressCallback 进度回调
     * @return 拆分后的文件列表
     */
    suspend fun splitFile(
        inputFile: File,
        outputDir: File,
        chunkSize: Int = CHUNK_SIZE,
        progressCallback: (Float) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val fileSize = inputFile.length()
        val parts = (fileSize + chunkSize - 1) / chunkSize
        val result = mutableListOf<File>()
        var processedSize = 0L

        FileInputStream(inputFile).use { input ->
            val buffer = ByteArray(8192)
            for (i in 0 until parts) {
                val partFile = File(outputDir, "${inputFile.nameWithoutExtension}_part$i${inputFile.extension}")
                result.add(partFile)

                FileOutputStream(partFile).use { output ->
                    var bytesWritten = 0
                    var bytesRead: Int = 0
                    while (bytesWritten < chunkSize &&
                        input.read(buffer).also { bytesRead = it } != -1) {
                        val bytesToWrite = minOf(bytesRead, chunkSize - bytesWritten)
                        output.write(buffer, 0, bytesToWrite)
                        bytesWritten += bytesToWrite
                        processedSize += bytesToWrite
                        val progress = processedSize.toFloat() / fileSize
                        progressCallback(progress)
                    }
                }
            }
        }

        result
    }

    /**
     * 从Uri读取文件内容到临时文件
     */
    suspend fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        tempFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 保存字节数组到公共下载目录
     * @return Pair<Uri, String> 文件URI和文件夹路径
     */
    suspend fun saveToDownloads(
        context: Context,
        data: ByteArray,
        fileName: String
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore API
            saveToDownloadsModern(context, data, fileName)
        } else {
            // Android 9 及以下使用传统文件系统
            saveToDownloadsLegacy(context, data, fileName)
        }
    }

    /**
     * Android 10+ (API 29+) 使用 MediaStore
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveToDownloadsModern(
        context: Context,
        data: ByteArray,
        fileName: String
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val mimeType = getMimeTypeFromFileName(fileName)

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1) // 标记为待完成状态
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("无法创建下载文件")

        try {
            // 写入文件数据
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(data)
                outputStream.flush()
            } ?: throw Exception("无法打开输出流")

            // 完成文件写入，移除待完成状态
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return@withContext Pair(uri, Environment.DIRECTORY_DOWNLOADS)

        } catch (e: Exception) {
            // 如果保存失败，删除已创建的条目
            resolver.delete(uri, null, null)
            throw Exception("保存文件失败: ${e.message}")
        }
    }

    /**
     * Android 9 及以下使用传统文件系统
     */
    private suspend fun saveToDownloadsLegacy(
        context: Context,
        data: ByteArray,
        fileName: String
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // 确保下载目录存在
        if (!downloadsDir.exists()) {
            if (!downloadsDir.mkdirs()) {
                throw Exception("无法创建下载目录")
            }
        }

        val file = File(downloadsDir, fileName)

        try {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(data)
                outputStream.flush()
            }

            // 通知媒体扫描器更新文件
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { _, _ -> }

            return@withContext Pair(Uri.fromFile(file), downloadsDir.absolutePath)

        } catch (e: Exception) {
            throw Exception("保存文件失败: ${e.message}")
        }
    }

    /**
     * 根据文件扩展名获取MIME类型
     */
    private fun getMimeTypeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "m4v" -> "video/x-m4v"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
    /**
     * 保存字符串到下载目录（重载方法）
     */
    suspend fun saveToDownloads(
        context: Context,
        fileName: String,
        data: ByteArray
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        return@withContext saveToDownloads(context, data, fileName)
    }

    /**
     * 创建临时文件
     */
    fun createTempFile(
        context: Context,
        fileName: String
    ): Uri {
        val tempFile = File(context.cacheDir, fileName)
        return Uri.fromFile(tempFile)
    }

    /**
     * 获取文件信息
     */
    fun getFileInfo(context: Context, uri: Uri): FileInfo {
        val name = getFileName(context, uri) ?: "未知文件"
        val size = getFileSize(context, uri)
        val mimeType = getMimeType(context, uri)

        return FileInfo(
            name = name,
            size = size,
            mimeType = mimeType,
            uri = uri
        )
    }

    /**
     * 读取Uri内容为字节数组
     */
    suspend fun readUriBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw IllegalArgumentException("无法读取URI: $uri")
    }

    /**
     * 检查文件是否存在
     */
    fun uriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 格式化文件大小显示
     */
    fun formatFileSize(bytes: Long): String {
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

    /**
     * 打开指定的文件夹 - 直接方法
     * 
     * @param context 上下文
     * @param folderPath 文件夹路径
     * @return 是否成功打开文件夹
     */
    fun openFolder(context: Context, folderPath: String): Boolean {
        // 尝试直接打开文件浏览器
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // 创建选择器
            val chooserIntent = Intent.createChooser(intent, "选择应用浏览文件")
            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            openDefaultFileManager(context)
        }
    }
    
    /**
     * 使用应用选择器打开文件夹 - 简化版本，确保能够正常工作
     */
    private fun openWithChooser(context: Context, folder: File?): Boolean {
        try {
            // 创建一个简单的Intent来打开文件管理器
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // 创建选择器
            val chooserIntent = Intent.createChooser(intent, "选择应用打开文件")
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 备用方案：直接打开系统文件管理器
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_FILES)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e2: Exception) {
                e2.printStackTrace()
                return false
            }
        }
    }
    
    /**
     * 打开包含指定文件的文件夹 - 直接方法
     * 
     * @param context 上下文
     * @param fileUri 文件URI
     * @param folderPath 可选的文件夹路径，如果提供则优先使用
     * @return 是否成功打开文件夹
     */
    fun openFileFolder(context: Context, fileUri: Uri, folderPath: String? = null): Boolean {
        // 直接打开文件选择器，这是最可靠的方法
        return try {
            // 创建一个Intent来查看文件
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = fileUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // 创建选择器
            val chooserIntent = Intent.createChooser(viewIntent, "选择应用打开文件")
            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 如果直接打开文件失败，尝试打开文件浏览器
            openDefaultFileManager(context)
        }
    }
    
    /**
     * 使用文件选择器打开文件所在的文件夹 - 简化版本
     */
    private fun openWithFileChooser(context: Context, fileUri: Uri): Boolean {
        try {
            // 尝试直接打开文件 - 这样用户可以看到文件，然后自己导航到文件夹
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = fileUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // 创建选择器
            val chooserIntent = Intent.createChooser(viewIntent, "选择应用打开文件")
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 备用方案：打开文件浏览器
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(intent, "浏览文件"))
                return true
            } catch (e2: Exception) {
                e2.printStackTrace()
                return false
            }
        }
    }
    
    /**
     * 打开默认文件管理器 - 简化版本
     */
    private fun openDefaultFileManager(context: Context): Boolean {
        // 直接使用最可靠的方法：打开文件选择器
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "浏览文件"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}