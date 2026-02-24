package com.example.myapplication

data class BluetoothDevice(
    val id: String,
    val name: String,
    val tirePressure: Double,
    val batteryLevel: Int,
    val connectionStatus: String,
    val lastUpdated: String
) {
    override fun toString(): String {
        return "$name ($id)"
    }
}