package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AssetAdapter
    private lateinit var logoutButton: Button

    // List for the adapter
    private val assetsList = mutableListOf<Asset>()

    private val CHANNEL_ID = "alarm_notifications"

    // Permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startForegroundScanService()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to scan for devices", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize API client with stored credentials
        ApiClient.init(this)

        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.deviceList)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        logoutButton = findViewById(R.id.logoutButton)

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AssetAdapter(assetsList)
        recyclerView.adapter = adapter

        // Set up swipe to refresh
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Scanning continuously in background", Toast.LENGTH_SHORT).show()
        }

        // Set up logout button
        logoutButton.setOnClickListener {
            logout()
        }

        // Notification channel
        createNotificationChannel()

        // Observe LiveData from repository
        SensorRepository.assetsLiveData.observe(this, Observer { assets ->
            Log.d("MainActivity", "LiveData updated: ${assets.size} assets")
            assetsList.clear()
            assetsList.addAll(assets)
            adapter.notifyDataSetChanged()
        })

        // Request permissions and start service
        checkPermissionsAndStartService()
    }

    private fun checkPermissionsAndStartService() {
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
            startForegroundScanService()
        } else {
            requestPermissionLauncher.launch(permissions)
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

    private fun startForegroundScanService() {
        val serviceIntent = Intent(this, ScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d("MainActivity", "Foreground scan service started")
    }

    private fun logout() {
        // Clear saved credentials
        val sharedPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        // Clear repository data
        SensorRepository.clearAll()

        // Stop scanning service
        stopService(Intent(this, ScanService::class.java))

        // Navigate back to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}