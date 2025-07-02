package com.ccdyz.tools.data.repository

import com.ccdyz.tools.data.database.entities.ScanHistory
import com.ccdyz.tools.data.database.entities.HostHistory
import com.ccdyz.tools.utils.AppPreferencesManager
import com.ccdyz.tools.utils.AppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import java.util.Date
import java.util.UUID

class ScanRepository {
    
    private val preferencesManager = AppPreferencesManager(AppContext.get())
    
    // 使用专用的协程作用域，避免使用GlobalScope
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _scanHistory = MutableStateFlow<List<ScanHistory>>(emptyList())
    val scanHistory: Flow<List<ScanHistory>> = _scanHistory.asStateFlow()
    
    private val _hostHistory = MutableStateFlow<List<HostHistory>>(emptyList())
    val hostHistory: Flow<List<HostHistory>> = _hostHistory.asStateFlow()
    
    init {
        // 异步加载持久化数据，优化启动性能
        repositoryScope.launch {
            try {
                val scanHistory = preferencesManager.loadScanHistory()
                val hostHistory = preferencesManager.loadHostHistory()
                _scanHistory.value = scanHistory
                _hostHistory.value = hostHistory
            } catch (e: Exception) {
                // 加载失败时使用空列表，避免应用崩溃
                _scanHistory.value = emptyList()
                _hostHistory.value = emptyList()
            }
        }
    }
    
    /**
     * 添加扫描历史记录
     */
    suspend fun addScanHistory(history: ScanHistory) = withContext(Dispatchers.IO) {
        val currentList = _scanHistory.value.toMutableList()
        currentList.add(0, history) // 添加到列表开头
        
        // 限制历史记录数量，保留最新的50条
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        
        _scanHistory.value = currentList
        
        // 异步持久化保存，避免阻塞
        repositoryScope.launch {
            try {
                preferencesManager.saveScanHistory(currentList)
            } catch (e: Exception) {
                // 保存失败时记录日志，但不影响UI
                // 可以在这里添加日志记录
            }
        }
    }
    
    /**
     * 清除所有扫描历史
     */
    suspend fun clearScanHistory() {
        _scanHistory.value = emptyList()
        preferencesManager.saveScanHistory(emptyList())
    }
    
    /**
     * 删除指定的扫描历史
     */
    suspend fun deleteScanHistory(historyId: String) = withContext(Dispatchers.IO) {
        val currentList = _scanHistory.value.toMutableList()
        currentList.removeAll { it.id == historyId }
        _scanHistory.value = currentList
        
        // 异步保存
        repositoryScope.launch {
            try {
                preferencesManager.saveScanHistory(currentList)
            } catch (e: Exception) {
                // 保存失败时记录日志
            }
        }
    }
    
    /**
     * 添加或更新主机名历史
     */
    suspend fun addOrUpdateHostHistory(hostname: String) = withContext(Dispatchers.IO) {
        val currentList = _hostHistory.value.toMutableList()
        val existingHost = currentList.find { it.hostname.equals(hostname, ignoreCase = true) }
        
        if (existingHost != null) {
            // 更新现有主机记录
            currentList.remove(existingHost)
            currentList.add(0, existingHost.copy(
                lastUsed = Date(),
                useCount = existingHost.useCount + 1
            ))
        } else {
            // 添加新主机记录
            currentList.add(0, HostHistory(
                id = UUID.randomUUID().toString(),
                hostname = hostname,
                lastUsed = Date(),
                useCount = 1
            ))
        }
        
        // 限制主机历史数量，保留最新的20条
        if (currentList.size > 20) {
            currentList.removeAt(currentList.size - 1)
        }
        
        _hostHistory.value = currentList
        
        // 异步保存
        repositoryScope.launch {
            try {
                preferencesManager.saveHostHistory(currentList)
            } catch (e: Exception) {
                // 保存失败时记录日志
            }
        }
    }
    
    /**
     * 获取主机名建议列表（按使用频率和时间排序）
     */
    fun getHostSuggestions(query: String): List<String> {
        return _hostHistory.value
            .filter { it.hostname.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<HostHistory> { it.useCount }.thenByDescending { it.lastUsed })
            .map { it.hostname }
            .take(5)
    }
    
    /**
     * 删除指定的主机历史
     */
    suspend fun deleteHostHistory(hostname: String) {
        val currentList = _hostHistory.value.toMutableList()
        currentList.removeAll { it.hostname.equals(hostname, ignoreCase = true) }
        _hostHistory.value = currentList
        preferencesManager.saveHostHistory(currentList)
    }
    
    /**
     * 清除所有主机历史
     */
    suspend fun clearHostHistory() {
        _hostHistory.value = emptyList()
        preferencesManager.saveHostHistory(emptyList())
    }
    
    /**
     * 清理资源，取消所有正在进行的协程
     */
    fun cleanup() {
        repositoryScope.cancel()
    }
}