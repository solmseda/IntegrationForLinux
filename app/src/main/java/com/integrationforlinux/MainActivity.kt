package com.integrationforlinux

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
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
    private lateinit var discoveredDevicesTextView: TextView

    private var selectedDevice: BluetoothDevice? = null
    private var discoveredDevicesCount = 0
    private var isRequestingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothConnectionManager = BluetoothConnectionManager(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Verifica se o dispositivo conta com Bluetooth
        if (!bluetoothConnectionManager.checkBluetoothAvailability(bluetoothAdapter)) finish()

        // Solicita permissões necessárias para Bluetooth
        bluetoothConnectionManager.checkAndRequestBluetoothPermissions()

        // Inicializa os componentes da UI
        statusTextView = findViewById(R.id.statusTextView)
        listView = findViewById(R.id.deviceListView)
        discoveredDevicesTextView = findViewById(R.id.discoveredDevicesTextView)

        // Iniciar o servidor automaticamente e tornar o dispositivo visível
        bluetoothConnectionManager.startServer()
        statusTextView.text = "Servidor Bluetooth iniciado automaticamente"

        // Torna o dispositivo visível para outros dispositivos Bluetooth
        makeDeviceDiscoverable()

        // Registro para buscar dispositivos Bluetooth
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        listView.setOnItemClickListener { _, _, position, _ ->
            val deviceInfo = listView.getItemAtPosition(position) as String
            val deviceAddress = deviceInfo.substring(deviceInfo.length - 17)
            selectedDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            statusTextView.text = "Dispositivo selecionado: $deviceAddress"
        }
    }

    private fun makeDeviceDiscoverable() {
        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)  // Visível por 300 segundos
        }
        startActivity(discoverableIntent)
        statusTextView.text = "O dispositivo está visível para outros dispositivos."
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    val deviceName = it.name ?: "Unknown"
                    val deviceAddress = it.address
                    addDeviceToList(deviceName, deviceAddress)
                }
            }
        }
    }

    private fun addDeviceToList(deviceName: String, deviceAddress: String) {
        val deviceInfo = "$deviceName\n$deviceAddress"
        (listView.adapter as? ArrayAdapter<String>)?.apply {
            add(deviceInfo)
            notifyDataSetChanged()
        }
        discoveredDevicesCount++
        discoveredDevicesTextView.text = "Dispositivos descobertos: $discoveredDevicesCount"
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
