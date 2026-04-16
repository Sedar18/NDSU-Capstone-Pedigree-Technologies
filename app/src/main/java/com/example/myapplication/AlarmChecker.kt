package com.example.myapplication

import android.util.Log

// Updated sealed class for API results
sealed class AlarmCheckResult {
    data class Success(
        val assetId: String,
        val assetName: String,
        val alarms: List<String>  // Changed from alarmCount to list of alarm names
    ) : AlarmCheckResult()
    data class Error(val message: String) : AlarmCheckResult()
}

class AlarmChecker {

    suspend fun checkSensorForAlarms(sensorName: String): AlarmCheckResult {
        return try {
            Log.d("AlarmChecker", "Checking sensor: $sensorName")

            // Step 1: Get asset ID from sensor name
            val assetResponse = ApiClient.api.getAssetFromSensor(sensorName)

            if (!assetResponse.isSuccessful || assetResponse.body() == null) {
                Log.e("AlarmChecker", "Failed to get asset for sensor $sensorName")
                return AlarmCheckResult.Error("Sensor not found in system")
            }

            val asset = assetResponse.body()!!
            val assetId = asset.dbId
            val assetName = asset.name ?: "Unknown Vehicle"

            Log.d("AlarmChecker", "Found asset: $assetName (ID: $assetId)")

            // Step 2: Get alarms for that asset
            val alarmsResponse = ApiClient.api.getAlarmsForAsset(assetId)

            if (!alarmsResponse.isSuccessful) {
                Log.e("AlarmChecker", "Failed to get alarms for asset $assetId")
                return AlarmCheckResult.Error("Failed to check alarms")
            }

            val alarmsData = alarmsResponse.body() ?: emptyList()

            // Extract alarm names from response
            val alarmNames = alarmsData.mapNotNull { alarm ->
                alarm.alarmConfig?.name ?: alarm.alarmType ?: alarm.description ?: "Unknown Alarm"
            }

            Log.d("AlarmChecker", "Found ${alarmNames.size} alarms for $assetName: $alarmNames")

            // Return success with asset info and alarm names
            AlarmCheckResult.Success(
                assetId = assetId,
                assetName = assetName,
                alarms = alarmNames
            )

        } catch (e: Exception) {
            Log.e("AlarmChecker", "Error checking sensor: ${e.message}", e)
            AlarmCheckResult.Error("Network error: ${e.message}")
        }
    }
}