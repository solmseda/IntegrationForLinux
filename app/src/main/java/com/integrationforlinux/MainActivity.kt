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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 100
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

        // Se for Android 12+, pede BLUETOOTH_CONNECT e afins em runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            // Android < 12: não exige runtime, pode iniciar direto
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

    /**
     * Chama o singleton para iniciar o servidor Bluetooth e, em seguida,
     * monta toda a interface (botões, listagem, etc.).
     */
    private fun inicializarBluetoothECarregarUI() {
        // 1) Inicializa o singleton e abre o servidor (= aceita conexões do Linux)
        BluetoothSingleton.init(this)
        Log.d("MainActivity", "Inicializando BluetoothSingleton")
        BluetoothSingleton.startServerAndAutoConnect()
        Log.d("MainActivity", "Servidor Bluetooth aguardando conexão persistente")

        // 2) Configura todas as Views
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        statusTextView = findViewById(R.id.statusTextView)
        discoverableCountdownTextView = findViewById(R.id.discoverableCountdownTextView)
        listView = findViewById(R.id.pairedDeviceListView)
        showPairedDevicesButton = findViewById(R.id.showPairedDevicesButton)
        enableDiscoverableButton = findViewById(R.id.enableDiscoverableButton)
        sendTestNotificationButton = findViewById(R.id.sendTestNotificationButton)

        // 3) Se o usuário ainda não habilitou o serviço de notificações, abre as configurações
        if (!isNotificationServiceEnabled(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // 4) Botão “Mostrar dispositivos pareados”
        showPairedDevicesButton.setOnClickListener {
            showPairedWithLinuxDevices()
        }

        // 5) Botão “Tornar visível via Bluetooth”
        enableDiscoverableButton.setOnClickListener {
            makeDeviceDiscoverable()
        }

        // 6) Botão “Enviar notificação de teste”
        sendTestNotificationButton.setOnClickListener {
            val testNotification = NotificationData(
                appName = "Aplicação de Teste",
                content = "Esta é uma notificação de teste enviada via Bluetooth.",
                icon = null,
                key = "test_notification_key_${System.currentTimeMillis()}" // Adiciona uma chave única para o teste
            )
            BluetoothSingleton.sendNotification(testNotification)
            statusTextView.text = "Notificação de teste enviada"
        }

        // 7) Recebe broadcast de mudança no modo de descoberta para atualizar o timer
        registerReceiver(
            discoverabilityReceiver,
            IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        )
    }

    /**
     * Checa no Settings se o NotificationListenerService está ativo para este app.
     */
    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return !TextUtils.isEmpty(flat) && flat.contains(pkgName)
    }

    /**
     * Torna o dispositivo Android descoberto por 300 segundos,
     * e exibe um CountDownTimer na tela.
     */
    private fun makeDeviceDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
        startCountdownTimer(300)
    }

    /**
     * Mostra um CountDownTimer de 300 segundos para visibilidade.
     */
    private fun startCountdownTimer(durationSeconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
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

    /**
     * Recebe broadcast de mudança no modo de descoberta
     * para atualizar o timer na UI.
     */
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

    /**
     * Lista, na ListView, todos os dispositivos pareados cujo nome
     * contenha "Integration4Linux" (útil para debug/visualizar).
     */
    private fun showPairedWithLinuxDevices() {
        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList = ArrayList<String>()

        for (device in pairedDevices) {
            if (device.name.contains("Integration4Linux", ignoreCase = true)) {
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address
                deviceList.add("$deviceName\n$deviceAddress")
            }
        }

        statusTextView.text =
            if (deviceList.isNotEmpty()) "Dispositivos pareados com a aplicação Linux"
            else "Nenhum dispositivo Linux pareado encontrado"

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoverabilityReceiver)
        // BluetoothSingleton.closeServer() 
    }
}
