package com.ccdyz.tools.data.database.entities

import java.util.Date

data class FileHistory(
    val id: String,
    val operation: String, // "merge" or "split"
    val fileName: String,
    val fileSize: Long,
    val operationTime: Date,
    val success: Boolean,
    val errorMessage: String? = null
)