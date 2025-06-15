package com.yusuf.deney2.util

import android.content.Context
import android.util.Log
import com.yusuf.deney2.database.SecurityDatabase
import com.yusuf.deney2.database.SecurityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SecurityLogManager {

    private var database: SecurityDatabase? = null
    private val logs = mutableListOf<SecurityLogEntry>()
    private val listeners = mutableListOf<(List<SecurityLogEntry>) -> Unit>()

    fun init(context: Context) {
        database = SecurityDatabase.getDatabase(context)
        loadLogsFromDatabase()
    }

    data class SecurityLogEntry(
        val id: Int,
        val appName: String,
        val packageName: String,
        val isThreat: Boolean,
        val threatScore: Float,
        val timestamp: Long,
        val actionType: String,
        val description: String
    ) {
        fun getFormattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    fun addLog(
        appName: String,
        packageName: String,
        isThreat: Boolean,
        threatScore: Float,
        actionType: String,
        description: String
    ) {
        val log = SecurityLogEntry(
            id = (logs.maxOfOrNull { it.id } ?: 0) + 1,
            appName = appName,
            packageName = packageName,
            isThreat = isThreat,
            threatScore = threatScore,
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            description = description
        )

        logs.add(0, log)
        saveLogToDatabase(log)

        val logLevel = if (isThreat) Log.WARN else Log.INFO
        Log.println(logLevel, "SecurityLog", "$actionType: $appName - $description (Skor: $threatScore)")

        notifyListeners()
    }

    private fun saveLogToDatabase(log: SecurityLogEntry) {
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val securityLog = SecurityLog(
                        appName = log.appName,
                        packageName = log.packageName,
                        isThreat = log.isThreat,
                        threatScore = log.threatScore,
                        timestamp = log.timestamp,
                        actionType = log.actionType,
                        description = log.description
                    )
                    db.securityLogDao().insertLog(securityLog)
                } catch (e: Exception) {
                    Log.e("SecurityLogManager", "Database kayıt hatası: ${e.message}")
                }
            }
        }
    }

    private fun loadLogsFromDatabase() {
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val securityLogs = db.securityLogDao().getAllLogsDirect()
                    withContext(Dispatchers.Main) {
                        logs.clear()
                        logs.addAll(securityLogs.map { dbLog ->
                            SecurityLogEntry(
                                id = dbLog.id,
                                appName = dbLog.appName,
                                packageName = dbLog.packageName,
                                isThreat = dbLog.isThreat,
                                threatScore = dbLog.threatScore,
                                timestamp = dbLog.timestamp,
                                actionType = dbLog.actionType,
                                description = dbLog.description
                            )
                        })
                        notifyListeners()
                    }
                } catch (e: Exception) {
                    Log.e("SecurityLogManager", "Database yükleme hatası: ${e.message}")
                }
            }
        }
    }

    fun getAllLogs(): List<SecurityLogEntry> = logs.toList()

    fun getThreatLogs(): List<SecurityLogEntry> = logs.filter { it.isThreat }

    fun getThreatCount(): Int = logs.count { it.isThreat }

    fun clearAllLogs() {
        logs.clear()
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.securityLogDao().deleteAllLogs()
                } catch (e: Exception) {
                    Log.e("SecurityLogManager", "Database temizleme hatası: ${e.message}")
                }
            }
        }

        notifyListeners()
    }

    fun addListener(listener: (List<SecurityLogEntry>) -> Unit) {
        listeners.add(listener)
        listener(logs)
    }

    fun removeListener(listener: (List<SecurityLogEntry>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it(logs) }
    }

    object ActionTypes {
        const val SCAN_STARTED = "TARAMA_BAŞLADI"
        const val SCAN_COMPLETED = "TARAMA_TAMAMLANDI"
        const val THREAT_DETECTED = "TEHDİT_TESPİT_EDİLDİ"
        const val APP_BLOCKED = "UYGULAMA_ENGELLENDİ"
        const val NOTIFICATION_SENT = "BİLDİRİM_GÖNDERİLDİ"
        const val NETWORK_BLOCKED = "AĞ_ERİŞİMİ_ENGELLENDİ"
        const val BACKGROUND_SCAN = "ARKA_PLAN_TARAMA"
        const val SERVICE_STARTED = "SERVİS_BAŞLATILDI"
        const val SERVICE_STOPPED = "SERVİS_DURDURULDU"
        const val ERROR = "HATA"
    }
}
