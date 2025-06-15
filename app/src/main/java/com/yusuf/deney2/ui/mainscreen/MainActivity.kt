package com.yusuf.deney2.ui.mainscreen

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.yusuf.deney2.R
import com.yusuf.deney2.databinding.ActivityMainBinding
import com.yusuf.deney2.service.SecurityMonitorService
import com.yusuf.deney2.util.ModelLoader
import com.yusuf.deney2.util.NotificationHelper
import com.yusuf.deney2.util.SecurityLogManager

class MainActivity : ComponentActivity() {

    private var isServiceRunning = false
    private var currentLogFilter = "ALL"

    private lateinit var binding: ActivityMainBinding
    private lateinit var securityLogAdapter: SecurityLogAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✅ Bildirim izni verildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Bildirim izni gerekli", Toast.LENGTH_LONG).show()
        }
        checkUsageStatsPermission()
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (hasUsageStatsPermission()) {
            Toast.makeText(this, "✅ Uygulama kullanım izni verildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Uygulama kullanım izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()

        ModelLoader.initModel(this)
        NotificationHelper.createNotificationChannels(this)
        SecurityLogManager.init(this)

        checkAndRequestPermissions()

        startSecurityService()
        setupLogListener()
        updateLogStats()
        updateLogDisplay()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        checkUsageStatsPermission()
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            showUsageStatsDialog()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsageStatsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔑 Önemli İzin Gerekli")
            .setMessage(
                "Güvenlik izleme için 'Uygulama Kullanım Erişimi' izni gerekli.\n\n" +
                    "Bu izin olmadan:\n" +
                    "• Arka plan uygulamaları tespit edilemez\n" +
                    "• Güvenlik taraması çalışmaz\n\n" +
                    "Ayarlar sayfasına yönlendirileceksiniz."
            )
            .setPositiveButton("Ayarlara Git") { _, _ ->
                openUsageStatsSettings()
            }
            .setNegativeButton("İptal") { _, _ ->
                Toast.makeText(this, "⚠️ İzin olmadan uygulama düzgün çalışmayacak", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun openUsageStatsSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ayarlar sayfası açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() = with(binding) {
        securityLogAdapter = SecurityLogAdapter()
        recyclerViewLogs.apply {
            adapter = securityLogAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() = with(binding) {
        serviceControlButton.setOnClickListener {
            if (isServiceRunning) {
                stopSecurityService()
            } else {
                startSecurityService()
            }
        }

        btnShowAllLogs.setOnClickListener {
            showAllLogs()
        }

        btnShowThreats.setOnClickListener {
            showThreatLogs()
        }

        btnClearLogs.setOnClickListener {
            clearLogs()
        }
    }

    private fun setupLogListener() {
        SecurityLogManager.addListener { logs ->
            runOnUiThread {
                updateLogStats()
                updateLogDisplay()
            }
        }
    }

    private fun showAllLogs() {
        currentLogFilter = "ALL"
        updateLogDisplay()
        updateTabButtons()
    }

    private fun showThreatLogs() {
        currentLogFilter = "THREATS"
        updateLogDisplay()
        updateTabButtons()
    }

    private fun clearLogs() {
        SecurityLogManager.clearAllLogs()
        updateLogStats()
        updateLogDisplay()
        Toast.makeText(this, "🗑️ Tüm loglar temizlendi", Toast.LENGTH_SHORT).show()
    }

    private fun updateTabButtons() = with(binding) {
        if (currentLogFilter == "ALL") {
            btnShowAllLogs.background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.button_selected)
            btnShowThreats.background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.button_unselected)
        } else {
            btnShowAllLogs.background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.button_unselected)
            btnShowThreats.background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.button_selected)
        }
    }

    private fun updateLogDisplay() = with(binding) {
        val logs = when (currentLogFilter) {
            "THREATS" -> SecurityLogManager.getThreatLogs()
            else -> SecurityLogManager.getAllLogs()
        }

        if (logs.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            recyclerViewLogs.visibility = View.GONE

            tvEmptyTitle.text = if (currentLogFilter == "THREATS") {
                "🛡️ Henüz tehdit tespit edilmedi!"
            } else {
                "📋 Henüz log bulunmuyor"
            }

            tvEmptyMessage.text = if (currentLogFilter == "THREATS") {
                "Güvenlik sistemi aktif olarak\nçalışıyor ve sizi koruyor"
            } else {
                "Güvenlik izleme başlatıldığında\nloglar burada görünecek..."
            }
        } else {
            emptyStateLayout.visibility = View.GONE
            recyclerViewLogs.visibility = View.VISIBLE
            securityLogAdapter.updateLogs(logs)
        }

        updateTabButtons()
    }

    private fun updateLogStats() {
        val totalLogs = SecurityLogManager.getAllLogs().size
        val threatCount = SecurityLogManager.getThreatCount()

        binding.tvTotalLogs.text = totalLogs.toString()
        binding.tvThreatCount.text = threatCount.toString()
    }

    private fun startSecurityService() {
        val intent = Intent(this, SecurityMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        updateServiceButtonState()

        SecurityLogManager.addLog(
            appName = "Sistem",
            packageName = packageName,
            isThreat = false,
            threatScore = 0.0f,
            actionType = "SERVİS_BAŞLATILDI",
            description = "Güvenlik izleme servisi başarıyla başlatıldı"
        )
    }

    private fun stopSecurityService() {
        val intent = Intent(this, SecurityMonitorService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateServiceButtonState()

        SecurityLogManager.addLog(
            appName = "Sistem",
            packageName = packageName,
            isThreat = false,
            threatScore = 0.0f,
            actionType = "SERVİS_DURDURULDU",
            description = "Güvenlik izleme servisi durduruldu"
        )
    }

    private fun updateServiceButtonState() = with(binding) {
        serviceControlButton.text = if (isServiceRunning) "İzlemeyi Durdur" else "İzlemeyi Başlat"

        serviceControlButton.backgroundTintList = if (isServiceRunning) {
            getColorStateList(R.color.status_danger)
        } else {
            getColorStateList(R.color.status_safe)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SecurityLogManager.removeListener { }
    }
}
