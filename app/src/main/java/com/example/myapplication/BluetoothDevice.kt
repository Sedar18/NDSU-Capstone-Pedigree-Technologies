package com.example.myapplication

data class BluetoothDevice(
    val id: String,
    val name: String,
    val tirePressure: Double,
    val batteryLevel: Int,
    val connectionStatus: String,
    val lastSeenTimestamp: Long  // milliseconds
) {
    override fun toString(): String {
        return "$name ($id)"
    }
}