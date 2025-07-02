package com.ccdyz.tools.data.database.entities

import java.util.Date

data class HostHistory(
    val id: String,
    val hostname: String,
    val lastUsed: Date,
    val useCount: Int = 1
)