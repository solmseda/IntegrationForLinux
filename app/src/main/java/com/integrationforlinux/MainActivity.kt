package com.integrationforlinux

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusTextView: TextView
    private lateinit var listView: ListView
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        listView = findViewById(R.id.deviceListView)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothConnectionManager = BluetoothConnectionManager(this)

        val createServerButton: Button = findViewById(R.id.createServerButton)
        val scanDevicesButton: Button = findViewById(R.id.scanDevicesButton)

        createServerButton.setOnClickListener {
            createServer()
        }

        scanDevicesButton.setOnClickListener {
            scanDevices()
        }

        // Register for broadcasts when a device is discovered
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    private fun createServer() {
        bluetoothConnectionManager.startServer()
        statusTextView.text = "Servidor criado com sucesso"
    }

    private fun scanDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_SCAN)
        } else {
            bluetoothAdapter.startDiscovery()
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    //val deviceName = it.name
                    val deviceName = "TESTE"
                    val deviceAddress = it.address // MAC address
                    addDeviceToList(deviceName ?: "Unknown", deviceAddress)
                }
            }
        }
    }

    private fun addDeviceToList(deviceName: String, deviceAddress: String) {
        val adapter = listView.adapter as? ArrayAdapter<String>
        adapter?.add("$deviceName\n$deviceAddress")
        adapter?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the broadcast receiver
        unregisterReceiver(receiver)
    }

    companion object {
        private const val REQUEST_BLUETOOTH_SCAN = 2
    }
}