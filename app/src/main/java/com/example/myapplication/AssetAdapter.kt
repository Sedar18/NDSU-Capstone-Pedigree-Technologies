package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AssetAdapter(private val assets: List<Asset>) : RecyclerView.Adapter<AssetAdapter.AssetViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val asset = assets[position]
        holder.bind(asset)
    }

    override fun getItemCount(): Int = assets.size

    inner class AssetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val tirePressure: TextView = itemView.findViewById(R.id.tirePressure)
        private val batteryLevel: TextView = itemView.findViewById(R.id.batteryLevel)
        private val connectionStatus: TextView = itemView.findViewById(R.id.connectionStatus)
        private val lastUpdated: TextView = itemView.findViewById(R.id.lastUpdated)

        fun bind(asset: Asset) {
            // Show asset name and sensor count
            val sensorInfo = asset.sensors.joinToString("\n") { "  • ${it.macAddress}" }
            deviceName.text = "${asset.assetName}\n${sensorInfo}"

            // Show alarm status
            val alarmStatus = if (asset.alarmCount > 0) {
                "🚨 ${asset.alarmCount} Active Alarm${if (asset.alarmCount > 1) "s" else ""}"
            } else {
                "✅ No Alarms"
            }
            tirePressure.text = alarmStatus

            // Show sensor count
            batteryLevel.text = "${asset.sensors.size} Sensor${if (asset.sensors.size > 1) "s" else ""} Detected"

            // Show asset ID for debugging
            connectionStatus.text = "Asset ID: ${asset.assetId}"

            // Show last seen time
            lastUpdated.text = "Last seen: ${getTimeAgo(asset.lastSeenTimestamp)}"
        }

        private fun getTimeAgo(timestamp: Long): String {
            val currentTime = System.currentTimeMillis()
            val diffInSeconds = (currentTime - timestamp) / 1000

            return when {
                diffInSeconds < 10 -> "Just now"
                diffInSeconds < 60 -> "$diffInSeconds seconds ago"
                diffInSeconds < 120 -> "1 minute ago"
                diffInSeconds < 3600 -> "${diffInSeconds / 60} minutes ago"
                else -> "${diffInSeconds / 3600} hours ago"
            }
        }
    }
}