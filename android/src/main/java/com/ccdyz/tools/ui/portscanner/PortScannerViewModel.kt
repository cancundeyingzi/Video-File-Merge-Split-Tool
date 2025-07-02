// app/src/main/java/ui/portscanner/PortScannerViewModel.kt
package com.ccdyz.tools.ui.portscanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ccdyz.tools.data.database.entities.ScanHistory
import com.ccdyz.tools.data.repository.ScanRepository
import com.ccdyz.tools.utils.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 根据设备性能获取最优线程数 - 基于Python代码的逻辑
 */
private fun getOptimalThreadCount(): Int {
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    // 网络I/O密集型任务，使用更激进的线程数
    // 大幅提升并发数以最大化扫描速度
    return minOf(availableProcessors * 8, 1000).coerceAtLeast(100)
}

data class PortScannerUiState(
    val hostname: String = "",
    val startPort: String = "1",
    val endPort: String = "65535",
    val timeout: String = "1000", // 优化：降低默认超时到1秒
    val threadCount: String = getOptimalThreadCount().toString(),
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val scannedPorts: Int = 0,
    val totalPorts: Int = 0,
    val currentPort: Int = 0,
    val openPorts: List<Int> = emptyList(),
    val scanHistory: List<ScanHistory> = emptyList(),
    val hostSuggestions: List<String> = emptyList(),
    val showHostSuggestions: Boolean = false,
    val errorMessage: String? = null,
    val scanSpeed: Float = 0f, // 扫描速度 (端口/秒)
    val estimatedTimeRemaining: Long = 0L // 预计剩余时间 (毫秒)
)

class PortScannerViewModel(
    private val repository: ScanRepository = ScanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortScannerUiState())
    val uiState: StateFlow<PortScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // 加载扫描历史
        viewModelScope.launch {
            repository.scanHistory.collect { history ->
                _uiState.update { it.copy(scanHistory = history) }
            }
        }
        
        // 加载主机历史
        viewModelScope.launch {
            repository.hostHistory.collect { hostHistory ->
                val suggestions = hostHistory.map { it.hostname }
                _uiState.update { it.copy(hostSuggestions = suggestions) }
            }
        }
    }

    fun updateHostname(hostname: String) {
        _uiState.update { 
            it.copy(
                hostname = hostname,
                showHostSuggestions = hostname.isNotBlank(),
                hostSuggestions = if (hostname.isNotBlank()) {
                    repository.getHostSuggestions(hostname)
                } else {
                    emptyList()
                }
            ) 
        }
    }
    
    fun selectHostSuggestion(hostname: String) {
        _uiState.update { 
            it.copy(
                hostname = hostname,
                showHostSuggestions = false
            ) 
        }
    }
    
    fun hideHostSuggestions() {
        _uiState.update { it.copy(showHostSuggestions = false) }
    }

    fun updateStartPort(port: String) {
        _uiState.update { it.copy(startPort = port) }
    }

    fun updateEndPort(port: String) {
        _uiState.update { it.copy(endPort = port) }
    }

    fun updateTimeout(timeout: String) {
        _uiState.update { it.copy(timeout = timeout) }
    }

    fun updateThreadCount(count: String) {
        _uiState.update { it.copy(threadCount = count) }
    }

    fun startScan() {
        val state = _uiState.value

        // 验证输入
        if (!validateInputs(state)) return

        val startPort = state.startPort.toIntOrNull() ?: 1
        val endPort = state.endPort.toIntOrNull() ?: 65535
        val timeout = state.timeout.toIntOrNull() ?: 1000
        val threadCount = state.threadCount.toIntOrNull() ?: 100
        // 确保超时时间和并发数在合理范围内
        val effectiveTimeout = timeout.coerceIn(100, 30000)
        val effectiveThreadCount = threadCount.coerceIn(1, 1000)

        val totalPorts = endPort - startPort + 1

        // 立即更新UI状态，显示扫描已开始
        _uiState.update {
            it.copy(
                isScanning = true,
                progress = 0f,
                scannedPorts = 0,
                totalPorts = totalPorts,
                openPorts = emptyList(),
                errorMessage = null
            )
        }

        // 在后台添加主机名到历史记录，避免阻塞主扫描流程
        viewModelScope.launch(Dispatchers.IO) {
            repository.addOrUpdateHostHistory(state.hostname)
        }

        // 启动扫描任务
        scanJob = viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // 使用高性能扫描算法
                val openPorts = performOptimizedScan(
                    hostname = state.hostname,
                    startPort = startPort,
                    endPort = endPort,
                    timeout = effectiveTimeout,
                    threadCount = effectiveThreadCount,
                    totalPorts = totalPorts
                )

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // 在后台保存扫描历史，避免阻塞UI更新
                viewModelScope.launch(Dispatchers.IO) {
                    val scanHistory = ScanHistory(
                        id = UUID.randomUUID().toString(),
                        hostname = state.hostname,
                        portRange = "$startPort-$endPort",
                        openPorts = openPorts.joinToString(","),
                        totalPorts = totalPorts,
                        openPortsCount = openPorts.size,
                        scanTime = Date(),
                        duration = duration
                    )
                    
                    repository.addScanHistory(scanHistory)
                }

                // 更新UI状态，显示扫描已完成
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        progress = 1f
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "扫描失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 基于Python ThreadPool逻辑的端口扫描实现
     * 特点：
     * 1. 分批处理端口，每批处理固定数量的端口
     * 2. 严格遵循用户设置的并发数
     * 3. 使用无锁数据结构存储结果
     * 4. 实时更新扫描进度
     */
    private suspend fun performOptimizedScan(
        hostname: String,
        startPort: Int,
        endPort: Int,
        timeout: Int,
        threadCount: Int,
        totalPorts: Int
    ): List<Int> {
        // 使用原子计数器和无锁队列提高性能
        val scannedCounter = AtomicInteger(0)
        val openPorts = ConcurrentLinkedQueue<Int>()
        
        // 预先解析主机名，避免每个连接都解析
        val effectiveHostname = if (NetworkUtils.isIpAddress(hostname)) {
            // 如果已经是IP地址，直接使用，避免不必要的解析
            hostname
        } else {
            // 否则尝试解析主机名
            val resolvedAddress = withContext(Dispatchers.IO) {
                try {
                    // 尝试解析主机名并缓存结果
                    val addr = java.net.InetAddress.getByName(hostname)
                    
                    // 返回解析后的IP地址字符串，避免后续重复解析
                    addr.hostAddress ?: hostname
                } catch (e: Exception) {
                    // 解析失败，返回原始主机名
                    null
                }
            }
            
            if (resolvedAddress == null) {
                throw Exception("无法解析主机名: $hostname")
            }
            
            resolvedAddress
        }
        
        // 启动进度更新协程
        val updateInterval = calculateProgressUpdateInterval(totalPorts)
        val scanStartTime = System.currentTimeMillis()
        val progressUpdateJob = viewModelScope.launch {
            var lastUpdateTime = 0L
            var lastScannedCount = 0
            
            while (scannedCounter.get() < totalPorts && isActive) {
                val currentTime = System.currentTimeMillis()
                val scanned = scannedCounter.get()
                val progress = scanned.toFloat() / totalPorts
                
                // 智能更新：时间间隔或进度变化达到阈值时更新
                if (currentTime - lastUpdateTime >= updateInterval || 
                    scanned - lastScannedCount >= totalPorts / 100) {
                    
                    // 计算性能统计
                    val elapsedTime = currentTime - scanStartTime
                    val scanSpeed = if (elapsedTime > 0) {
                        (scanned * 1000f) / elapsedTime // 端口/秒
                    } else 0f
                    
                    val estimatedTimeRemaining = if (scanSpeed > 0 && scanned > 0) {
                        ((totalPorts - scanned) / scanSpeed * 1000).toLong()
                    } else 0L
                    
                    _uiState.update { state ->
                        state.copy(
                            scannedPorts = scanned,
                            progress = progress,
                            openPorts = openPorts.toList(), // 避免频繁排序，只在最后排序
                            scanSpeed = scanSpeed,
                            estimatedTimeRemaining = estimatedTimeRemaining
                        )
                    }
                    
                    lastUpdateTime = currentTime
                    lastScannedCount = scanned
                }
                
                delay(updateInterval)
            }
        }
        
        try {
            // 按照Python代码的逻辑，分批处理端口
            // 每批处理的端口数量等于并发数
            val batchSize = threadCount
            var currentPort = startPort
            
            while (currentPort <= endPort) {
                // 计算当前批次的结束端口
                val batchEndPort = minOf(currentPort + batchSize - 1, endPort)
                val currentBatchSize = batchEndPort - currentPort + 1
                
                // 创建当前批次的端口列表
                val portBatch = (currentPort..batchEndPort).toList()
                
                // 使用协程并发扫描当前批次的所有端口
                val batchResults = withContext(Dispatchers.IO) {
                    portBatch.map { port ->
                        async {
                            try {
                                val isOpen = NetworkUtils.scanPort(effectiveHostname, port, timeout)
                                if (isOpen) {
                                    // 打印开放端口信息，与Python版本一致
                                    println("Ip:$effectiveHostname Port:$port IS OPEN")
                                    port
                                } else null
                            } catch (e: Exception) {
                                // 捕获并打印异常，但返回null表示端口未开放
                                println("Error scanning port $port: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                
                // 添加开放的端口到结果列表
                openPorts.addAll(batchResults)
                
                // 更新已扫描端口计数
                scannedCounter.addAndGet(currentBatchSize)
                
                // 移动到下一批
                currentPort = batchEndPort + 1
            }
            
        } finally {
            // 停止进度更新
            progressUpdateJob.cancel()
            
            // 最终更新进度
            _uiState.update { state ->
                state.copy(
                    scannedPorts = totalPorts,
                    progress = 1f,
                    openPorts = openPorts.sorted()
                )
            }
        }
        
        return openPorts.sorted()
    }

    // 扩展函数：向上取整
    private fun Float.ceil(): Int = kotlin.math.ceil(this).toInt()

    /**
     * 计算进度更新间隔 - 极致优化版本
     * 根据扫描规模智能调整更新频率，平衡响应性和性能
     * 减少UI更新频率以提高扫描性能
     */
    private fun calculateProgressUpdateInterval(totalPorts: Int): Long {
        return when {
            totalPorts <= 100 -> 100L // 小范围：100ms更新一次
            totalPorts <= 1000 -> 250L // 中等范围：250ms更新一次
            totalPorts <= 10000 -> 500L // 大范围：500ms更新一次
            else -> 1000L // 超大范围：1000ms更新一次，大幅减少UI压力
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                isScanning = false,
                progress = 0f
            )
        }
    }

    fun deleteHistory(history: ScanHistory) {
        viewModelScope.launch {
            repository.deleteScanHistory(history.id)
        }
    }

    private fun validateInputs(state: PortScannerUiState): Boolean {
        if (state.hostname.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入主机名或IP地址") }
            return false
        }

        if (!NetworkUtils.isValidHost(state.hostname)) {
            _uiState.update { it.copy(errorMessage = "主机名或IP地址格式不正确") }
            return false
        }

        val startPort = state.startPort.toIntOrNull()
        val endPort = state.endPort.toIntOrNull()
        val timeout = state.timeout.toIntOrNull()
        val threadCount = state.threadCount.toIntOrNull()

        if (startPort == null || startPort < 1 || startPort > 65535) {
            _uiState.update { it.copy(errorMessage = "起始端口必须在1-65535之间") }
            return false
        }

        if (endPort == null || endPort < 1 || endPort > 65535) {
            _uiState.update { it.copy(errorMessage = "结束端口必须在1-65535之间") }
            return false
        }

        if (startPort > endPort) {
            _uiState.update { it.copy(errorMessage = "起始端口不能大于结束端口") }
            return false
        }

        if (timeout == null || timeout < 100 || timeout > 30000) {
            _uiState.update { it.copy(errorMessage = "超时时间必须在100-30000毫秒之间") }
            return false
        }

        if (threadCount == null || threadCount < 1 || threadCount > 1000) {
            _uiState.update { it.copy(errorMessage = "并发数必须在1-1000之间") }
            return false
        }

        val totalPorts = endPort - startPort + 1
        if (totalPorts > 60000) {
            _uiState.update { it.copy(errorMessage = "端口范围过大，建议不超过60000个端口") }
            return false
        }

        return true
    }
}

