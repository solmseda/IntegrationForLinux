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
    private var isRequestingPermission = false // Flag para evitar múltiplas solicitações de permissão


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        if(!checkPermissions()){
            requestBluetoothPermissions()
        }

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

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Permissões necessárias para o o android 12 e superior (API 31 e superior)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permissões necessárias para o o android 11 e anterior
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun requestBluetoothPermissions() {
        if (!isRequestingPermission) {
            isRequestingPermission = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ),
                REQUEST_BLUETOOTH_SCAN
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isRequestingPermission = false
        if (requestCode == REQUEST_BLUETOOTH_SCAN) {
            if ((grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })) {
                Log.i("BluetoothScan", "Permissões concedidas. Pronto para iniciar o scan.")
                discoveredDevicesTextView.text = "Permissão para Bluetooth concedida. Pronto para procurar dispositivos"
            } else {
                Log.w("BluetoothScan", "Permissão para Bluetooth não concedida")
                discoveredDevicesTextView.text = "Permissão para Bluetooth não concedida. Não é possível escanear dispositivos."
            }
        }
    }

    private fun createServer() {
        bluetoothConnectionManager.startServer()
        statusTextView.text = "Servidor criado com sucesso"
    }

    private fun scanDevices() {
        Log.d("BluetoothScan", "scanDevices chamado")
        discoveredDevicesTextView.text = ""

        if (!checkPermissions()) {
            Log.w("BluetoothScan", "Permissões Bluetooth ainda não foram concedidas")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BluetoothScan", "Adaptador Bluetooth não está disponível ou não está ativado.")
            discoveredDevicesTextView.text = "Bluetooth não está ativado. Ative-o e tente novamente."
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        try {
            Log.i("BluetoothScan", "Tentando iniciar descoberta de dispositivos...")
            val discoveryStarted = bluetoothAdapter.startDiscovery()
            Log.d("BluetoothScan", "startDiscovery() retornou $discoveryStarted")

            if (discoveryStarted) {
                discoveredDevicesTextView.text = "Procurando dispositivos..."
            } else {
                Log.e("BluetoothScan", "Falha ao iniciar descoberta de dispositivos.")
                discoveredDevicesTextView.text = "Erro ao iniciar a busca por dispositivos. Tente novamente."
            }
        } catch (e: Exception) {
            Log.e("BluetoothScan", "Erro ao tentar iniciar a descoberta: ${e.message}", e)
            discoveredDevicesTextView.text = "Erro ao iniciar a descoberta: ${e.message}"
        }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    companion object {
        private const val REQUEST_BLUETOOTH_SCAN = 2
    }
}
