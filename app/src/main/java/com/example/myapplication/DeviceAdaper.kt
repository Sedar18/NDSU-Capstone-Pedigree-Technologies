package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val devices: List<BluetoothDevice>) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val tirePressure: TextView = itemView.findViewById(R.id.tirePressure)
        private val batteryLevel: TextView = itemView.findViewById(R.id.batteryLevel)
        private val connectionStatus: TextView = itemView.findViewById(R.id.connectionStatus)
        private val lastUpdated: TextView = itemView.findViewById(R.id.lastUpdated)

        fun bind(device: BluetoothDevice) {
            deviceName.text = device.name
            tirePressure.text = "Tire Pressure: ${device.tirePressure} PSI"
            batteryLevel.text = "Battery: ${device.batteryLevel}%"
            connectionStatus.text = "Status: ${device.connectionStatus}"
            lastUpdated.text = "Updated: ${device.lastUpdated}"
        }
    }
}