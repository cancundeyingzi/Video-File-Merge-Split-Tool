package com.ccdyz.tools

import com.ccdyz.tools.utils.NetworkUtils
import org.junit.Test
import org.junit.Assert.*

class PortScannerTest {

    @Test
    fun testHostValidation() {
        // 测试有效的IP地址
        assertTrue(NetworkUtils.isValidHost("192.168.1.1"))
        assertTrue(NetworkUtils.isValidHost("127.0.0.1"))
        assertTrue(NetworkUtils.isValidHost("8.8.8.8"))
        
        // 测试有效的域名
        assertTrue(NetworkUtils.isValidHost("google.com"))
        assertTrue(NetworkUtils.isValidHost("www.example.com"))
        assertTrue(NetworkUtils.isValidHost("sub.domain.com"))
        
        // 测试无效的输入
        assertFalse(NetworkUtils.isValidHost(""))
        assertFalse(NetworkUtils.isValidHost("256.256.256.256"))
        assertFalse(NetworkUtils.isValidHost("invalid..domain"))
        assertFalse(NetworkUtils.isValidHost("192.168.1"))
    }

    @Test
    fun testPortValidation() {
        // 测试有效端口
        assertTrue(NetworkUtils.isValidPort(1))
        assertTrue(NetworkUtils.isValidPort(80))
        assertTrue(NetworkUtils.isValidPort(443))
        assertTrue(NetworkUtils.isValidPort(65535))
        
        // 测试无效端口
        assertFalse(NetworkUtils.isValidPort(0))
        assertFalse(NetworkUtils.isValidPort(-1))
        assertFalse(NetworkUtils.isValidPort(65536))
        assertFalse(NetworkUtils.isValidPort(100000))
    }
}