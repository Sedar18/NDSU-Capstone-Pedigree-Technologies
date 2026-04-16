package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AssetAdapter

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    // HashMap to store assets (Asset ID as key)
    private val assetsMap = mutableMapOf<String, Asset>()

    // List for the adapter
    private val assetsList = mutableListOf<Asset>()

    // Whitelist prefix for Pedigree sensors
    private val PEDIGREE_MAC_PREFIX = "34:EE:2"

    // Alarm checker instance
    private val alarmChecker = AlarmChecker()

    // Notification channel ID
    private val CHANNEL_ID = "alarm_notifications"
    private var notificationId = 1000

    // Track which sensors we've already checked
    private val checkedSensors = mutableSetOf<String>()

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBleScan()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to scan for devices", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views
        recyclerView = findViewById(R.id.deviceList)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AssetAdapter(assetsList)
        recyclerView.adapter = adapter

        // Set up swipe-to-refresh
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Scanning continuously", Toast.LENGTH_SHORT).show()
        }

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Create notification channel
        createNotificationChannel()

        // Request permissions and start scanning
        checkPermissionsAndScan()

        // Start UI refresh task to update "last seen" times
        startUiRefreshTask()

        // Start foreground scanning service
        startForegroundScanService()
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val allPermissionsGranted = permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun startBleScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (!scanning) {
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
            Toast.makeText(this, "Scanning for Pedigree sensors...", Toast.LENGTH_SHORT).show()
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }

                val device = it.device
                val deviceAddress = device.address

                // Filter by Pedigree MAC prefix
                if (!deviceAddress.startsWith(PEDIGREE_MAC_PREFIX, ignoreCase = true)) {
                    return
                }

                val currentTime = System.currentTimeMillis()

                // Check if we've already looked up this sensor
                if (!checkedSensors.contains(deviceAddress)) {
                    checkedSensors.add(deviceAddress)
                    checkSensorForAsset(deviceAddress, currentTime)
                } else {
                    // Sensor already checked, just update timestamp in existing asset
                    updateSensorTimestamp(deviceAddress, currentTime)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(this@MainActivity, "Scan failed with error: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSensorForAsset(macAddress: String, timestamp: Long) {
        val sensorName = "BT_" + macAddress.replace(":", "")

        Log.d("MainActivity", "Checking asset for sensor: $sensorName")

        // Launch coroutine to check API
        lifecycleScope.launch {
            when (val result = alarmChecker.checkSensorForAlarms(sensorName)) {
                is AlarmCheckResult.Success -> {
                    Log.d("MainActivity", "Asset found: ${result.assetName} (${result.alarms.size} alarms)")
                    addOrUpdateAsset(result.assetId, result.assetName, macAddress, timestamp, result.alarms)

                    // Show notification and vibrate if alarms exist
                    if (result.alarms.isNotEmpty()) {
                        showAlarmNotification(result.assetName, result.alarms)
                        vibratePhone()
                    }
                }
                is AlarmCheckResult.Error -> {
                    Log.e("MainActivity", "Error checking sensor: ${result.message}")
                }
            }
        }
    }

    private fun addOrUpdateAsset(assetId: String, assetName: String, sensorMac: String, timestamp: Long, alarms: List<String>) {
        runOnUiThread {
            if (assetsMap.containsKey(assetId)) {
                // Asset exists, add sensor to it
                val asset = assetsMap[assetId]!!
                asset.addOrUpdateSensor(sensorMac, timestamp)
                asset.alarmCount = alarms.size
                asset.alarmNames = alarms // Store alarm names

                // Update in list
                val index = assetsList.indexOfFirst { it.assetId == assetId }
                if (index != -1) {
                    assetsList[index] = asset
                    sortAndRefreshList()
                }
            } else {
                // New asset, create it
                val newAsset = Asset(
                    assetId = assetId,
                    assetName = assetName,
                    sensors = mutableListOf(SensorInfo(sensorMac, timestamp)),
                    alarmCount = alarms.size,
                    alarmNames = alarms, // Store alarm names
                    lastSeenTimestamp = timestamp
                )

                assetsMap[assetId] = newAsset
                assetsList.add(newAsset)
                sortAndRefreshList()
            }
        }
    }

    private fun sortAndRefreshList() {
        // Sort: alarms first (by count descending), then alphabetically
        assetsList.sortWith(
            compareByDescending<Asset> { it.alarmCount }
                .thenBy { it.assetName }
        )
        adapter.notifyDataSetChanged()
    }

    private fun updateSensorTimestamp(sensorMac: String, timestamp: Long) {
        runOnUiThread {
            // Find which asset this sensor belongs to
            for (asset in assetsMap.values) {
                val sensor = asset.sensors.find { it.macAddress == sensorMac }
                if (sensor != null) {
                    asset.addOrUpdateSensor(sensorMac, timestamp)

                    val index = assetsList.indexOfFirst { it.assetId == asset.assetId }
                    if (index != -1) {
                        adapter.notifyItemChanged(index)
                    }
                    break
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Notifications"
            val descriptionText = "Notifications for vehicle alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAlarmNotification(assetName: String, alarms: List<String>) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MainActivity", "Notification permission not granted")
            return
        }

        // Format: "Nearby Alarm: Asset Name - Alarm Name"
        val alarmNames = alarms.joinToString(", ")
        val notificationTitle = "Nearby Alarm: $assetName - $alarmNames"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(notificationTitle)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId++, notification)
        }

        Log.d("MainActivity", "Notification sent for $assetName: $alarmNames")
    }

    private fun vibratePhone() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun startForegroundScanService() {
        val serviceIntent = Intent(this, ScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d("MainActivity", "Foreground scan service started")
    }

    private fun startUiRefreshTask() {
        handler.post(object : Runnable {
            override fun run() {
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }
                handler.postDelayed(this, 5000) // Refresh every 5 seconds
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }
}