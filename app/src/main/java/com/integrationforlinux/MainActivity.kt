package com.integrationforlinux

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager
    private lateinit var statusTextView: TextView
    private lateinit var discoverableCountdownTextView: TextView
    private lateinit var listView: ListView
    private lateinit var showPairedDevicesButton: Button
    private lateinit var enableDiscoverableButton: Button
    private lateinit var sendTestNotificationButton: Button
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o adaptador Bluetooth e BluetoothConnectionManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothConnectionManager = BluetoothConnectionManager(this)

        // Inicia automaticamente o servidor Bluetooth e adiciona um log
        Log.d("MainActivity", "Iniciando servidor Bluetooth...")
        bluetoothConnectionManager.startServer()
        Log.d("MainActivity", "Servidor Bluetooth iniciado automaticamente ao iniciar a MainActivity")

        bluetoothConnectionManager.autoConnectToPairedDevices()
        Log.d("MainActivity", "Conectando automaticamente aos dispositivos pareados ao iniciar a MainActivity")

        // Inicializa os componentes de UI
        statusTextView = findViewById(R.id.statusTextView)
        discoverableCountdownTextView = findViewById(R.id.discoverableCountdownTextView)
        listView = findViewById(R.id.pairedDeviceListView)
        showPairedDevicesButton = findViewById(R.id.showPairedDevicesButton)
        enableDiscoverableButton = findViewById(R.id.enableDiscoverableButton)
        sendTestNotificationButton = findViewById(R.id.sendTestNotificationButton)

        // Configura o botão para mostrar dispositivos pareados com a aplicação Linux
        showPairedDevicesButton.setOnClickListener {
            showPairedWithLinuxDevices()
        }

        // Configura o botão para permitir pareamento (visibilidade)
        enableDiscoverableButton.setOnClickListener {
            makeDeviceDiscoverable()
        }

        // Configura o botão para enviar uma notificação de teste
        sendTestNotificationButton.setOnClickListener {
            sendTestNotification()
        }

        // Registro para detectar quando o dispositivo deixa de ser descoberto
        registerReceiver(discoverabilityReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
    }

    private fun makeDeviceDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 300 segundos de visibilidade
        }
        startActivity(discoverableIntent)
        startCountdownTimer(300)
    }

    private fun startCountdownTimer(durationSeconds: Int) {
        countdownTimer?.cancel() // Cancela o timer anterior, se existir

        countdownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                discoverableCountdownTextView.text = "Tempo restante de visibilidade: $secondsRemaining segundos"
            }

            override fun onFinish() {
                discoverableCountdownTextView.text = "Visibilidade expirada"
            }
        }.start()
    }

    private val discoverabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE)
                if (scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    countdownTimer?.cancel()
                    discoverableCountdownTextView.text = "Visibilidade expirada"
                }
            }
        }
    }

    private fun showPairedWithLinuxDevices() {
        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList = ArrayList<String>()

        for (device in pairedDevices) {
            if (bluetoothConnectionManager.isDevicePaired(device.address)) {
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address
                deviceList.add("$deviceName\n$deviceAddress")
            }
        }

        if (deviceList.isNotEmpty()) {
            statusTextView.text = "Dispositivos pareados com a aplicação Linux"
        } else {
            statusTextView.text = "Nenhum dispositivo pareado com a aplicação Linux encontrado"
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = adapter
    }

    private fun sendTestNotification() {
        val testNotification = NotificationData(
            appName = "Aplicação de Teste",
            content = "Esta é uma notificação de teste enviada via Bluetooth.",
            icon = null
        )

        bluetoothConnectionManager.sendNotification(testNotification)
        statusTextView.text = "Notificação de teste enviada"
    }

    fun updateStatus(message: String) {
        statusTextView.text = message
    }

    fun showPairingStatus(deviceName: String, success: Boolean) {
        val statusMessage = if (success) {
            "Pareamento com $deviceName realizado com sucesso."
        } else {
            "Falha ao parear com $deviceName."
        }
        updateStatus(statusMessage)
    }

    fun showConnectionStatus(deviceName: String, success: Boolean) {
        val statusMessage = if (success) {
            "Conectado ao dispositivo $deviceName com sucesso."
        } else {
            "Falha ao conectar ao dispositivo $deviceName."
        }
        updateStatus(statusMessage)
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoverabilityReceiver)
    }
}
