package com.example.myapplication

data class SensorInfo(
    val macAddress: String,
    val lastSeenTimestamp: Long
)

data class Asset(
    val assetId: String,
    val assetName: String,
    val sensors: MutableList<SensorInfo>,
    var alarmCount: Int = 0,
    var lastSeenTimestamp: Long
) {
    fun updateLastSeen(timestamp: Long) {
        lastSeenTimestamp = timestamp
    }

    fun addOrUpdateSensor(macAddress: String, timestamp: Long) {
        val existingSensor = sensors.find { it.macAddress == macAddress }
        if (existingSensor != null) {
            sensors.remove(existingSensor)
        }
        sensors.add(SensorInfo(macAddress, timestamp))
        updateLastSeen(timestamp)
    }
}