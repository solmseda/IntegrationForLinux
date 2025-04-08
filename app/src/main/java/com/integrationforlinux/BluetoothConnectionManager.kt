package com.integrationforlinux

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import com.google.gson.Gson

class BluetoothConnectionManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 101
    }

    fun checkBluetoothAvailability(bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
    fun checkAndRequestBluetoothPermissions(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requer permissões específicas para Bluetooth
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                context as Activity,
                permissionsNeeded.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
            return false
        }

        return true
    }

    fun checkBluetoothEnabled(){
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(context, "Bluetooth is disabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            grantResults.forEach {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }
        return false
    }

    fun startServer() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT
            )
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                        "integrationforlinux", uuid
                    )

                    Log.d("BluetoothServer", "Servidor aguardando conexão com UUID: $uuid...")
                    val socket = serverSocket?.accept()

                    if (socket != null) {
                        Log.d("BluetoothServer", "Dispositivo conectado: ${socket.remoteDevice.name} (${socket.remoteDevice.address})")
                        manageConnection(socket)
                        serverSocket?.close()
                        Log.d("BluetoothServer", "Servidor reiniciado após conexão")
                    }

                } catch (e: IOException) {
                    Log.e("BluetoothServer", "Erro no servidor Bluetooth: ${e.message}")
                }
            }
        }
    }

    fun startClient(device: BluetoothDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    pairDevice(device)

                    Log.i("BluetoothClient", "Tentando criar socket de conexão com o dispositivo.")
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)

                    Log.i("BluetoothClient", "Tentando conectar ao dispositivo ${device.name} (${device.address})")
                    bluetoothSocket?.connect()

                    Log.i("BluetoothClient", "Conectado ao dispositivo ${device.name} (${device.address})")
                    manageConnection(bluetoothSocket)
                    (context as MainActivity).showConnectionStatus(device.name ?: "Desconhecido", true)
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Erro de I/O ao tentar conectar: ${e.message}", e)
                    (context as MainActivity).showConnectionStatus(device.name ?: "Desconhecido", false)
                } catch (e: Exception) {
                    Log.e("BluetoothClient", "Erro inesperado ao tentar conectar: ${e.message}", e)
                    (context as MainActivity).showConnectionStatus(device.name ?: "Desconhecido", false)
                }
            } else {
                Log.e("BluetoothClient", "Permissão BLUETOOTH_CONNECT não concedida.")
                (context as MainActivity).updateStatus("Permissão Bluetooth não concedida.")
            }
        }
    }


    private fun manageConnection(socket: BluetoothSocket?) {
        socket?.let {
            try {
                inputStream = it.inputStream
                outputStream = it.outputStream
                Log.d("BluetoothConnection", "Conexão gerenciada com sucesso")
            } catch (e: IOException) {
                Log.e("BluetoothConnection", "Erro ao gerenciar a conexão: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun authenticate() {
        // TODO
    }

    fun sendNotification(notificationData: NotificationData) {
        try {
            val gson = Gson()
            val jsonData = gson.toJson(notificationData)
            Log.d("BluetoothSend", "Enviando JSON: $jsonData") // <= AQUI
            outputStream?.write(jsonData.toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e("BluetoothSend", "Erro ao enviar notificação: ${e.message}")
        }
    }


    fun receiveClipboardData(): ClipboardData? {
        return try {
            val buffer = ByteArray(1024)
            val data = StringBuilder()
            var bytesRead: Int
            while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                data.append(String(buffer, 0, bytesRead))
            }
            ClipboardData(data.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isDevicePaired(deviceAddress: String): Boolean {
        val sharedPrefs = context.getSharedPreferences("PairedDevices", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(deviceAddress, false)
    }

    fun pairDevice(device: BluetoothDevice) {
        try {
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                device.createBond()
                Log.d("BluetoothConnection", "Iniciando pareamento com ${device.name} (${device.address})")
                (context as MainActivity).showPairingStatus(device.name ?: "Desconhecido", true)
            } else {
                Log.d("BluetoothConnection", "Dispositivo já está pareado: ${device.name} (${device.address})")
                (context as MainActivity).updateStatus("Dispositivo já está pareado")
            }
        } catch (e: Exception) {
            Log.e("BluetoothConnection", "Erro ao parear dispositivo: ${e.message}")
            (context as MainActivity).showPairingStatus(device.name ?: "Desconhecido", false)
            e.printStackTrace()
        }
    }

    fun autoConnectToPairedDevices() {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return
        for (device in pairedDevices) {
            if (device.name.contains("Integration4Linux", ignoreCase = true)) {
                startClient(device)
                break // ou continue tentando todos
            }
        }
    }


    fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}