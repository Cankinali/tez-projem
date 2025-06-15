package com.yusuf.deney2.ui.mainscreen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yusuf.deney2.R
import com.yusuf.deney2.util.SecurityLogManager
import java.text.SimpleDateFormat
import java.util.*

class SecurityLogAdapter : RecyclerView.Adapter<SecurityLogAdapter.LogViewHolder>() {

    private var logs = listOf<SecurityLogManager.SecurityLogEntry>()

    fun updateLogs(newLogs: List<SecurityLogManager.SecurityLogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLogIcon: TextView = itemView.findViewById(R.id.tvLogIcon)
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvActionType: TextView = itemView.findViewById(R.id.tvActionType)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvThreatScore: TextView = itemView.findViewById(R.id.tvThreatScore)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)

        fun bind(log: SecurityLogManager.SecurityLogEntry) {
            // İkonu belirle
            tvLogIcon.text = when (log.actionType) {
                "SERVİS_BAŞLATILDI" -> "🟢"
                "SERVİS_DURDURULDU" -> "🔴"
                "TARAMA_BAŞLADI" -> "🔍"
                "TARAMA_TAMAMLANDI" -> "✅"
                "TEHDİT_TESPİT_EDİLDİ" -> "⚠️"
                "BİLDİRİM_GÖNDERİLDİ" -> "📢"
                "UYGULAMA_ENGELLENDİ" -> "🚫"
                "AĞ_ERİŞİMİ_ENGELLENDİ" -> "🔒"
                "ARKA_PLAN_TARAMA" -> "🔄"
                "UYGULAMA_KALDIRILDI" -> "🗑️"
                "HATA" -> "❌"
                else -> "📝"
            }

            // Uygulama adı
            tvAppName.text = log.appName

            // Aksiyon türü
            tvActionType.text = log.actionType

            // Zaman formatı
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            tvTime.text = dateFormat.format(Date(log.timestamp))

            // Threat score formatting
            if (log.threatScore > 0) {
                tvThreatScore.visibility = View.VISIBLE
                tvThreatScore.text = "Skor: ${String.format("%.2f", log.threatScore)}"

                // Score rengini belirle
                tvThreatScore.setTextColor(
                    if (log.isThreat) {
                        ContextCompat.getColor(itemView.context, R.color.status_danger)
                    } else {
                        ContextCompat.getColor(itemView.context, R.color.status_safe)
                    }
                )
            } else {
                tvThreatScore.visibility = View.GONE
            }

            // Text color
            val textColor = if (log.isThreat) {
                ContextCompat.getColor(itemView.context, R.color.status_danger)
            } else {
                when (log.actionType) {
                    "SERVİS_BAŞLATILDI", "TARAMA_TAMAMLANDI" ->
                        ContextCompat.getColor(itemView.context, R.color.status_safe)

                    else ->
                        ContextCompat.getColor(itemView.context, R.color.text_primary)
                }
            }

            tvActionType.setTextColor(textColor)
            tvAppName.setTextColor(
                if (log.isThreat) {
                    ContextCompat.getColor(itemView.context, R.color.status_danger)
                } else {
                    ContextCompat.getColor(itemView.context, R.color.text_primary)
                }
            )

            // Açıklama
            tvDescription.text = log.description

            // Package name
            tvPackageName.text = log.packageName
        }
    }
}
