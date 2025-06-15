package com.yusuf.deney2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_logs")
data class SecurityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appName: String,
    val packageName: String,
    val isThreat: Boolean,
    val threatScore: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val description: String
)