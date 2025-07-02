package com.ccdyz.tools.data.database.entities

import java.util.Date

data class ScanHistory(
    val id: String,
    val hostname: String,
    val portRange: String,
    val openPorts: String, // JSON格式存储开放端口
    val totalPorts: Int,
    val openPortsCount: Int,
    val scanTime: Date,
    val duration: Long // 扫描耗时(毫秒)
)