package com.integrationforlinux

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 100
        private const val REQUEST_DISCOVERABLE = 1
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
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

        // Solicita permissões em runtime para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            // Android <12: inicializa diretamente
            inicializarBluetoothECarregarUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val todasConcedidas = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (todasConcedidas) {
                inicializarBluetoothECarregarUI()
            } else {
                Toast.makeText(
                    this,
                    "Permissões de Bluetooth são necessárias para o app funcionar.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DISCOVERABLE && resultCode > 0) {
            // Usuário aceitou ser discoverable: inicia o servidor Bluetooth
            BluetoothSingleton.startServerAndAutoConnect()
            statusTextView.text = "Servidor Bluetooth iniciado"
            Log.d("MainActivity", "Servidor Bluetooth iniciado após discoverable")
        }
    }

    private fun inicializarBluetoothECarregarUI() {
        // 1) Inicializa o singleton (servidor será iniciado após permissões ou discoverable)
        BluetoothSingleton.init(this)
        Log.d("MainActivity", "BluetoothSingleton inicializado")

        // 2) Configura Views
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        statusTextView = findViewById(R.id.statusTextView)
        discoverableCountdownTextView = findViewById(R.id.discoverableCountdownTextView)
        listView = findViewById(R.id.pairedDeviceListView)
        showPairedDevicesButton = findViewById(R.id.showPairedDevicesButton)
        enableDiscoverableButton = findViewById(R.id.enableDiscoverableButton)
        sendTestNotificationButton = findViewById(R.id.sendTestNotificationButton)

        // Se o NotificationListenerService não estiver ativo, abre as configurações
        if (!isNotificationServiceEnabled(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Botão: mostrar dispositivos pareados com Linux
        showPairedDevicesButton.setOnClickListener {
            showPairedWithLinuxDevices()
        }

        // Botão: tornar dispositivo discoverable
        enableDiscoverableButton.setOnClickListener {
            makeDeviceDiscoverable()
        }

        // Botão: enviar notificação de teste via Bluetooth
        sendTestNotificationButton.setOnClickListener {
            val testNotification = NotificationData(
                appName = "Aplicação de Teste",
                content = "Esta é uma notificação de teste enviada via Bluetooth.",
                icon = null,
                key = "test_notification_key_${System.currentTimeMillis()}"
            )
            BluetoothSingleton.sendNotification(testNotification)
            statusTextView.text = "Notificação de teste enviada"
        }

        // Receiver para atualização do timer de discoverable
        registerReceiver(
            discoverabilityReceiver,
            IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        )
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return !TextUtils.isEmpty(flat) && flat.contains(pkgName)
    }

    private fun makeDeviceDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE)
        startCountdownTimer(300)
    }

    private fun startCountdownTimer(durationSeconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                discoverableCountdownTextView.text =
                    "Tempo restante de visibilidade: $secondsRemaining segundos"
            }
            override fun onFinish() {
                discoverableCountdownTextView.text = "Visibilidade expirada"
            }
        }.start()
    }

    private val discoverabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val scanMode = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_SCAN_MODE,
                    BluetoothAdapter.SCAN_MODE_NONE
                )
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
            if (device.name.contains("Integration4Linux", ignoreCase = true)) {
                deviceList.add("${device.name}\n${device.address}")
            }
        }
        statusTextView.text = if (deviceList.isNotEmpty())
            "Dispositivos pareados com a aplicação Linux"
        else
            "Nenhum dispositivo Linux pareado encontrado"
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            deviceList
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoverabilityReceiver)
    }
}
