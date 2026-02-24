package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Find the RecyclerView from the layout
        val recyclerView = findViewById<RecyclerView>(R.id.deviceList)

        // 2. Create some fake Bluetooth devices
        val fakeDevices = listOf(
            BluetoothDevice("AA:BB:CC:DD:EE:FF", "Device A", 32.5, 85, "Connected", "2024-01-15 10:30"),
            BluetoothDevice("11:22:33:44:55:66", "Device B", 31.8, 92, "Connected", "2024-01-15 10:28"),
            BluetoothDevice("77:88:99:AA:BB:CC", "Device C", 33.2, 78, "Disconnected", "2024-01-15 09:45"),
            BluetoothDevice("00:11:22:33:44:55", "Device D", 30.5, 88, "Connected", "2024-01-15 10:32"),
            BluetoothDevice("66:77:88:99:AA:BB", "Device E", 32.0, 95, "Connected", "2024-01-15 10:31")
        )

        // 3. Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DeviceAdapter(fakeDevices)
    }
}