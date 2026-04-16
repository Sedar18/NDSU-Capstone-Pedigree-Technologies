package com.example.myapplication

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*

class ScanService : Service() {

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val checkedSensors = mutableSetOf<String>()
    private val alarmChecker = AlarmChecker()

    private val PEDIGREE_MAC_PREFIX = "34:EE:2"
    private val CHANNEL_ID = "scan_service_channel"
    private val ALARM_CHANNEL_ID = "alarm_notifications"
    private val NOTIFICATION_ID = 1
    private var alarmNotificationId = 2000

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("ScanService", "Service created")

        // Create notification channels
        createNotificationChannels()

        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ScanService::WakeLock"
        )
        wakeLock?.acquire(10*60*60*1000L) // 10 hours max

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScanService", "Service started")

        // Start foreground with persistent notification
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start BLE scanning
        startBleScan()

        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ScanService", "Service destroyed")

        stopBleScan()
        serviceScope.cancel()
        wakeLock?.release()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for foreground service notification
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Scanning Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when app is scanning for sensors"
            }

            // Channel for alarm notifications
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for vehicle alarms"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        // Intent to open app when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanning for Sensors")
            .setContentText("Monitoring nearby Pedigree tire sensors...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Can't be dismissed
            .build()
    }

    private fun startBleScan() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ScanService", "Missing BLUETOOTH_SCAN permission")
            return
        }

        if (!scanning) {
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
            Log.d("ScanService", "BLE scan started")
        }
    }

    private fun stopBleScan() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (scanning) {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
            Log.d("ScanService", "BLE scan stopped")
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                if (ActivityCompat.checkSelfPermission(this@ScanService, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }

                val deviceAddress = it.device.address

                // Filter by Pedigree MAC prefix
                if (!deviceAddress.startsWith(PEDIGREE_MAC_PREFIX, ignoreCase = true)) {
                    return
                }

                // Check if we've already looked up this sensor
                if (!checkedSensors.contains(deviceAddress)) {
                    checkedSensors.add(deviceAddress)
                    checkSensorForAlarms(deviceAddress)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("ScanService", "Scan failed with error: $errorCode")
        }
    }

    private fun checkSensorForAlarms(macAddress: String) {
        val sensorName = "BT_" + macAddress.replace(":", "")

        Log.d("ScanService", "Checking sensor: $sensorName")

        serviceScope.launch {
            when (val result = alarmChecker.checkSensorForAlarms(sensorName)) {
                is AlarmCheckResult.Success -> {
                    Log.d("ScanService", "Asset found: ${result.assetName} (${result.alarms.size} alarms)")

                    if (result.alarms.isNotEmpty()) {
                        showAlarmNotification(result.assetName, result.alarms)
                        vibratePhone()
                    }
                }
                is AlarmCheckResult.Error -> {
                    Log.e("ScanService", "Error checking sensor: ${result.message}")
                }
            }
        }
    }

    private fun showAlarmNotification(assetName: String, alarms: List<String>) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("ScanService", "Notification permission not granted")
            return
        }

        val alarmNames = alarms.joinToString(", ")
        val notificationTitle = "Nearby Alarm: $assetName - $alarmNames"

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(notificationTitle)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(alarmNotificationId++, notification)
        }

        Log.d("ScanService", "Alarm notification sent for $assetName")
    }

    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
}