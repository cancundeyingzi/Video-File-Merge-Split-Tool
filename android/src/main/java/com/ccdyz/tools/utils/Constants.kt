package com.ccdyz.tools.utils

object Constants {
    // 网络扫描相关
    const val DEFAULT_TIMEOUT = 3000
    const val MAX_THREADS = 1000
    const val DEFAULT_THREADS = 100

    // 端口范围
    const val MIN_PORT = 1
    const val MAX_PORT = 65535

    // 文件处理相关
    const val CHUNK_SIZE = 1024 * 1024 // 1MB
    const val MAX_FILE_SIZE = 8L * 1024 * 1024 * 1024 // 8GB

    // 数据库
    const val DATABASE_NAME = "tools_app_database"
    const val DATABASE_VERSION = 1
}