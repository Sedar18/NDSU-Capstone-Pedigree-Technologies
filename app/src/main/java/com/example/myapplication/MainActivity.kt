package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    // List to store discovered devices
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // Scan duration in milliseconds
    private val SCAN_PERIOD: Long = 10000

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
        adapter = DeviceAdapter(discoveredDevices)
        recyclerView.adapter = adapter

        // Set up swipe-to-refresh
        swipeRefreshLayout.setOnRefreshListener {
            refreshDevices()
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

        // Request permissions and start scanning
        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            // Clear previous results
            discoveredDevices.clear()
            adapter.notifyDataSetChanged()

            // Start scanning
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Scan complete. Found ${discoveredDevices.size} devices", Toast.LENGTH_SHORT).show()
            }, SCAN_PERIOD)

            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
            Toast.makeText(this, "Scanning for BLE devices...", Toast.LENGTH_SHORT).show()
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
                val deviceName = device.name ?: "Unknown Device"

                // Check if device already exists in list
                val existingDevice = discoveredDevices.find { d -> d.id == deviceAddress }

                if (existingDevice == null) {
                    // Add new device
                    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val newDevice = BluetoothDevice(
                        id = deviceAddress,
                        name = deviceName,
                        tirePressure = 0.0, // We'll update this when we connect and read data
                        batteryLevel = 0,
                        connectionStatus = "Discovered",
                        lastUpdated = currentTime
                    )

                    discoveredDevices.add(newDevice)

                    // Update UI on main thread
                    runOnUiThread {
                        adapter.notifyItemInserted(discoveredDevices.size - 1)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(this@MainActivity, "Scan failed with error: $errorCode", Toast.LENGTH_SHORT).show()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun refreshDevices() {
        if (!scanning) {
            startBleScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop scanning when activity is destroyed
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }
}