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
    private lateinit var discoveredDevicesTextView: TextView

    private var selectedDevice: BluetoothDevice? = null
    private var discoveredDevicesCount = 0
    private var isRequestingPermission = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica se o dispositivo conta com Bluetooth
        if(!bluetoothConnectionManager.checkBluetoothAvailability(bluetoothAdapter)) finish()

        bluetoothConnectionManager.checkBluetoothEnabled()

        bluetoothConnectionManager.checkAndRequestBluetoothPermissions()

        // Inicializar os componentes da UI
        statusTextView = findViewById(R.id.statusTextView)
        listView = findViewById(R.id.deviceListView)
        discoveredDevicesTextView = findViewById(R.id.discoveredDevicesTextView)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothConnectionManager = BluetoothConnectionManager(this)

        val createServerButton: Button = findViewById(R.id.createServerButton)
        val scanDevicesButton: Button = findViewById(R.id.scanDevicesButton)
        val startClientButton: Button = findViewById(R.id.startClientButton)

        // Configurar o ArrayAdapter para o ListView
        val deviceList: MutableList<String> = mutableListOf()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = adapter

        createServerButton.setOnClickListener {
            createServer()
        }

        scanDevicesButton.setOnClickListener {
            Log.d("MainActivity", "Botão Procurar Dispositivos clicado")
            scanDevices()
        }

        startClientButton.setOnClickListener {
            startClient()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        listView.setOnItemClickListener { _, _, position, _ ->
            val deviceInfo = listView.getItemAtPosition(position) as String
            val deviceAddress = deviceInfo.substring(deviceInfo.length - 17)
            selectedDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            statusTextView.text = "Dispositivo selecionado: $deviceAddress"
        }
    }

    private fun scanDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_SCAN
            )
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery() // Para qualquer busca anterior
        }

        statusTextView.text = "Procurando dispositivos..."
        discoveredDevicesTextView.text = "Dispositivos descobertos aparecerão abaixo."
        discoveredDevicesCount = 0 // Reinicializar o contador de dispositivos encontrados

        // Iniciar a busca por dispositivos Bluetooth
        bluetoothAdapter.startDiscovery()
    }

    private fun createServer() {
        bluetoothConnectionManager.startServer()
        statusTextView.text = "Servidor criado com sucesso"
    }

    private fun startClient() {
        selectedDevice?.let {
            bluetoothConnectionManager.startClient(it)
            statusTextView.text = "Tentando conectar ao dispositivo: ${it.name} (${it.address})"
        } ?: run {
            statusTextView.text = "Nenhum dispositivo selecionado."
        }
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
        // Incrementar o contador de dispositivos e atualizar o texto
        discoveredDevicesCount++
        discoveredDevicesTextView.text = "Dispositivos descobertos: $discoveredDevicesCount"
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    companion object {
        private const val REQUEST_BLUETOOTH_SCAN = 2
    }
}
