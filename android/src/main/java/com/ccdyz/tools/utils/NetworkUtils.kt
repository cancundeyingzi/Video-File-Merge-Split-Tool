package com.ccdyz.tools.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

object NetworkUtils {
    
    /**
     * 扫描单个端口 - 增强版，更准确地检测端口开放状态
     * 基于Python的socket连接逻辑，但增加了额外的验证步骤
     * @param host 主机地址
     * @param port 端口号
     * @param timeout 超时时间（毫秒）
     * @return 端口是否开放
     */
    suspend fun scanPort(host: String, port: Int, timeout: Int): Boolean {
        // 移除withContext，因为调用方已经在IO线程
        var socket: Socket? = null
        try {
            // 使用用户指定的超时时间
            socket = Socket()
            // 优化Socket参数以提高性能
            socket.soTimeout = timeout
            socket.tcpNoDelay = true  // 禁用Nagle算法，减少延迟
            socket.reuseAddress = true  // 允许地址重用
            socket.setPerformancePreferences(0, 1, 0)  // 优先考虑延迟而非带宽
            
            // 使用用户指定的连接超时
            socket.connect(InetSocketAddress(host, port), timeout)
            
            // 已知的问题端口列表，这些端口可能会产生假阳性结果
            val problematicPorts = setOf(513, 514, 1524, 2525, 3659, 4092, 6000)
            
            // 对于问题端口，进行额外验证
            if (port in problematicPorts) {
                return verifyPortIsReallyOpen(socket, port)
            }
            
            // 对于其他端口，连接成功就认为端口开放
            return true
        } catch (e: Exception) {
            // 连接失败，端口未开放
            return false
        } finally {
            // 手动关闭socket，避免use{}块的额外开销
            try {
                socket?.close()
            } catch (e: Exception) {
                // 忽略关闭异常
            }
        }
    }
    
    /**
     * 验证端口是否真正开放
     * 通过尝试发送数据和读取响应来确认
     * @param socket 已连接的Socket
     * @param port 端口号（用于特定端口的特殊处理）
     * @return 端口是否真正开放
     */
    private fun verifyPortIsReallyOpen(socket: Socket, port: Int): Boolean {
        try {
            // 设置一个更短的读取超时
            socket.soTimeout = 500
            
            // 尝试获取输入流和输出流
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            
            // 根据不同的端口发送适当的请求
            val request = when (port) {
                80, 8080, 8000, 8888 -> "HEAD / HTTP/1.1\r\nHost: ${socket.inetAddress.hostName}\r\n\r\n"
                21 -> "USER anonymous\r\n"
                25, 587, 2525 -> "EHLO example.com\r\n"
                110 -> "USER test\r\n"
                143 -> "A1 CAPABILITY\r\n"
                else -> "PING\r\n"
            }
            
            // 发送请求
            outputStream.write(request.toByteArray())
            outputStream.flush()
            
            // 尝试读取响应
            val buffer = ByteArray(1024)
            val bytesRead = try {
                inputStream.read(buffer, 0, buffer.size)
            } catch (e: Exception) {
                -1
            }
            
            // 如果能读取到数据，则端口确实开放
            return bytesRead > 0
        } catch (e: Exception) {
            // 如果发生异常，则认为端口未开放
            return false
        }
    }
    
    /**
     * 批量扫描端口 - 超高性能批处理方法
     * 使用激进的并发策略大幅提升扫描速度
     * @param host 主机地址
     * @param ports 端口列表
     * @param timeout 超时时间（毫秒）
     * @return 开放端口列表
     */
    suspend fun scanPortsBatch(host: String, ports: List<Int>, timeout: Int): List<Int> = withContext(Dispatchers.IO) {
        // 移除内部的Semaphore，让并发控制完全由外部处理
        // 这样可以确保严格遵循用户设置的并发数
        val jobs = ports.map { port ->
            async {
                if (scanPort(host, port, timeout)) port else null
            }
        }
        
        // 等待所有扫描完成并过滤出开放端口
        jobs.awaitAll().filterNotNull()
    }
    
    /**
     * 验证主机名或IP地址格式
     */
    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        
        // 检查是否为IP地址
        if (isIpAddress(host)) return true
        
        // 简单的域名验证
        val domainPattern = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*$""")
        return domainPattern.matches(host)
    }
    
    /**
     * 检查字符串是否为有效的IP地址
     */
    fun isIpAddress(host: String): Boolean {
        if (host.isBlank()) return false
        
        // 简单的IP地址验证
        val parts = host.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
    
    /**
     * 验证端口号
     */
    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
}