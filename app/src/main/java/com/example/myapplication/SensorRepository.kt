package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object SensorRepository {

    // HashMap to store assets
    private val assetsMap = mutableMapOf<String, Asset>()

    // LiveData for UI updates
    private val _assetsLiveData = MutableLiveData<List<Asset>>()
    val assetsLiveData: LiveData<List<Asset>> = _assetsLiveData

    // Track which assets we've already notified about
    private val notifiedAssets = mutableSetOf<String>()

    fun addOrUpdateAsset(
        assetId: String,
        assetName: String,
        sensorMac: String,
        timestamp: Long,
        alarms: List<String>
    ) {
        synchronized(this) {
            if (assetsMap.containsKey(assetId)) {
                // Asset exists, update it
                val asset = assetsMap[assetId]!!
                asset.addOrUpdateSensor(sensorMac, timestamp)
                asset.alarmCount = alarms.size
                asset.alarmNames = alarms
            } else {
                // New asset, create it
                val newAsset = Asset(
                    assetId = assetId,
                    assetName = assetName,
                    sensors = mutableListOf(SensorInfo(sensorMac, timestamp)),
                    alarmCount = alarms.size,
                    alarmNames = alarms,
                    lastSeenTimestamp = timestamp
                )
                assetsMap[assetId] = newAsset
            }

            // Update LiveData with sorted list
            updateLiveData()
        }
    }

    fun updateSensorTimestamp(sensorMac: String, timestamp: Long) {
        synchronized(this) {
            for (asset in assetsMap.values) {
                val sensor = asset.sensors.find { it.macAddress == sensorMac }
                if (sensor != null) {
                    asset.addOrUpdateSensor(sensorMac, timestamp)
                    updateLiveData()
                    break
                }
            }
        }
    }

    fun findAssetBySensorMac(macAddress: String): Asset? {
        synchronized(this) {
            return assetsMap.values.find { asset ->
                asset.sensors.any { it.macAddress == macAddress }
            }
        }
    }

    fun hasNotifiedAsset(assetId: String): Boolean {
        synchronized(this) {
            return notifiedAssets.contains(assetId)
        }
    }

    fun markAssetNotified(assetId: String) {
        synchronized(this) {
            notifiedAssets.add(assetId)
        }
    }

    fun getAllAssets(): List<Asset> {
        synchronized(this) {
            return assetsMap.values
                .sortedWith(
                    compareByDescending<Asset> { it.alarmCount }
                        .thenBy { it.assetName }
                )
        }
    }

    private fun updateLiveData() {
        _assetsLiveData.postValue(getAllAssets())
    }

    fun clearAll() {
        synchronized(this) {
            assetsMap.clear()
            notifiedAssets.clear()
            _assetsLiveData.postValue(emptyList())
        }
    }
}