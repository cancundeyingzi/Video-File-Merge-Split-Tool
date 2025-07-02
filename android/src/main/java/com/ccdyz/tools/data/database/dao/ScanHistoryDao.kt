package com.ccdyz.tools.data.database.dao

import com.ccdyz.tools.data.database.entities.ScanHistory
import kotlinx.coroutines.flow.Flow

// 简化的DAO接口，不使用Room数据库
interface ScanHistoryDao {
    fun getAllScans(): Flow<List<ScanHistory>>
    suspend fun insertScan(scan: ScanHistory)
    suspend fun deleteScan(scan: ScanHistory)
    suspend fun clearAll()
    fun getScansForHost(hostname: String): Flow<List<ScanHistory>>
}