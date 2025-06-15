package com.yusuf.deney2.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SecurityLogDao {

    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC")
    fun getAllLogs(): LiveData<List<SecurityLog>>

    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsDirect(): List<SecurityLog>

    @Query("SELECT * FROM security_logs WHERE isThreat = 1 ORDER BY timestamp DESC")
    fun getThreatLogs(): LiveData<List<SecurityLog>>

    @Insert
    suspend fun insertLog(log: SecurityLog)

    @Delete
    suspend fun deleteLog(log: SecurityLog)

    @Query("DELETE FROM security_logs")
    suspend fun deleteAllLogs()

    @Query("SELECT COUNT(*) FROM security_logs WHERE isThreat = 1")
    fun getThreatCount(): LiveData<Int>
}
