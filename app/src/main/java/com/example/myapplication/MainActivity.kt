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

        // 2. Create some fake Bluetooth device IDs
        val fakeDevices = listOf(
            "Device A (AA:BB:CC:DD:EE:FF)",
            "Device B (11:22:33:44:55:66)",
            "Device C (77:88:99:AA:BB:CC)",
            "Device D (00:11:22:33:44:55)",
            "Device E (66:77:88:99:AA:BB)"
        )

        // 3. Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DeviceAdapter(fakeDevices)
    }
}