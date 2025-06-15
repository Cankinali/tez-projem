package com.yusuf.deney2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yusuf.deney2.R
import com.yusuf.deney2.util.ModelLoader
import com.yusuf.deney2.util.NotificationHelper
import com.yusuf.deney2.util.SecurityLogManager

class SecurityMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val monitoringRunnable = object : Runnable {
        override fun run() {
            monitorRunningApps()
            handler.postDelayed(this, MONITORING_INTERVAL)
        }
    }

    private val checkedApps = mutableSetOf<String>()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "security_monitor_channel"
        private const val MONITORING_INTERVAL = 30000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NotificationHelper.createNotificationChannels(this)
        ModelLoader.initModel(this)
        Log.d("SecurityMonitor", "Güvenlik izleme servisi başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        Log.d("SecurityMonitor", "Güvenlik izleme servisi durduruldu")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Güvenlik İzleme",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka planda çalışan uygulamaları güvenlik açısından izler"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Güvenlik İzleme Aktif")
            .setContentText("Uygulamalar güvenlik açısından izleniyor...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        handler.post(monitoringRunnable)
        Log.d("SecurityMonitor", "İzleme başlatıldı")
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(monitoringRunnable)
        Log.d("SecurityMonitor", "İzleme durduruldu")
    }

    private fun monitorRunningApps() {
        try {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            val userApps = installedApps.filter { appInfo ->
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }

            Log.d("SecurityMonitor", "Toplam ${userApps.size} kullanıcı uygulaması kontrol ediliyor")

            userApps.forEach { appInfo ->
                val packageName = appInfo.packageName

                if (!checkedApps.contains(packageName)) {
                    analyzeApp(packageName)
                    checkedApps.add(packageName)
                }
            }

        } catch (e: Exception) {
            Log.e("SecurityMonitor", "Uygulama izleme hatası: ${e.message}")
        }
    }

    private fun analyzeApp(packageName: String) {
        try {
            Log.d("APK_SCAN", "Tarama yapılan paket: $packageName")
            val packageManager = packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()

            val features = extractAppFeatures(packageName)

            ModelLoader.testOnnxModel(
                inputArray = features,
                onResult = { prediction ->
                    val isSecure = prediction <= 0.5f
                    val status = if (isSecure) "GÜVENLİ" else "TEHLİKELİ"

                    Log.d("SecurityAnalysis", "$appLabel: $status (skor: $prediction)")

                    SecurityLogManager.addLog(
                        appName = appLabel,
                        packageName = packageName,
                        isThreat = !isSecure,
                        threatScore = prediction,
                        actionType = "ARKA_PLAN_TARAMA",
                        description = if (isSecure)
                            "Arka plan taramasında güvenli bulundu"
                        else
                            "Arka plan taramasında tehlikeli bulundu"
                    )

                    if (!isSecure) {
                        Log.w("SecurityAlert", "⚠️ Tehlikeli uygulama tespit edildi: $appLabel ($packageName)")

                        SecurityLogManager.addLog(
                            appName = appLabel,
                            packageName = packageName,
                            isThreat = true,
                            threatScore = prediction,
                            actionType = SecurityLogManager.ActionTypes.THREAT_DETECTED,
                            description = "Arka plan izlemede zararlı uygulama tespit edildi"
                        )

                        NotificationHelper.sendThreatNotification(this, appLabel)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SecurityMonitor", "Uygulama analizi hatası ($packageName): ${e.message}")
        }
    }

    private fun extractAppFeatures(packageName: String): FloatArray {
        val packageManager = packageManager
        val features = FloatArray(172)

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

            features[0] = if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) 1.0f else 0.0f
            features[1] = if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) 1.0f else 0.0f
            features[2] = packageInfo.requestedPermissions?.size?.toFloat() ?: 0.0f
            features[3] = appInfo.targetSdkVersion.toFloat() / 35.0f
            features[4] = (packageInfo.firstInstallTime / 1000000000f) % 1000f

            val dangerousPermissions = arrayOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.READ_SMS",
                "android.permission.CALL_PHONE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE"
            )

            dangerousPermissions.forEachIndexed { index, permission ->
                if (index < 50 && packageInfo.requestedPermissions?.contains(permission) == true) {
                    features[10 + index] = 1.0f
                }
            }

        } catch (e: Exception) {
            Log.e("FeatureExtraction", "Özellik çıkarma hatası ($packageName): ${e.message}")
        }

        return features
    }
}
