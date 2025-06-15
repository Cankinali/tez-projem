package com.yusuf.deney2.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.yusuf.deney2.R

object NotificationHelper {

    private const val SCAN_CHANNEL_ID = "app_scan_channel"
    private const val THREAT_CHANNEL_ID = "threat_alert_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val scanChannel = NotificationChannel(
                SCAN_CHANNEL_ID,
                "Uygulama Taraması",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Yeni yüklenen uygulamaların güvenlik taraması bildirimleri"
            }

            val threatChannel = NotificationChannel(
                THREAT_CHANNEL_ID,
                "Güvenlik Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tehlikeli uygulama tespit edildiğinde gösterilen uyarılar"
            }

            notificationManager.createNotificationChannel(scanChannel)
            notificationManager.createNotificationChannel(threatChannel)
        }
    }

    fun showScanNotification(context: Context, appName: String, isScanning: Boolean = true) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isScanning) {
            val notification = NotificationCompat.Builder(context, SCAN_CHANNEL_ID)
                .setContentTitle("🔍 Güvenlik Taraması")
                .setContentText("$appName uygulaması taranıyor...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()

            notificationManager.notify(1000, notification)
        } else {
            notificationManager.cancel(1000)
        }
    }

    fun showScanResultNotification(context: Context, appName: String, isSecure: Boolean, score: Float) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(1000)

        if (isSecure) {
            val notification = NotificationCompat.Builder(context, SCAN_CHANNEL_ID)
                .setContentTitle("✅ Güvenlik Taraması Tamamlandı")
                .setContentText("$appName uygulaması güvenli (Skor: ${String.format("%.2f", score)})")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(2000, notification)
        } else {
            val notification = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
                .setContentTitle("⚠️ TEHLİKELİ UYGULAMA TESPİT EDİLDİ!")
                .setContentText("$appName uygulaması tehlikeli olabilir (Skor: ${String.format("%.2f", score)})")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setOngoing(true)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$appName uygulaması güvenlik taramasında tehlikeli olarak işaretlendi. " +
                                "Bu uygulama kaldırılması önerilir. Güvenlik skoru: ${String.format("%.2f", score)}"
                        )
                )
                .build()

            notificationManager.notify(3000, notification)
        }
    }

    fun sendThreatNotification(context: Context, appName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
            .setContentTitle("⚠️ Şüpheli Uygulama Tespit Edildi")
            .setContentText("$appName uygulaması arka plan taramasında tehlikeli bulundu")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(4000, notification)
    }
}
