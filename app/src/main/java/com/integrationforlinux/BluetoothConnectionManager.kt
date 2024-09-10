package com.integrationforlinux

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1
    }

    fun startServer() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT)
        } else {
            AsyncTask.execute {
                try {
                    val serverSocket: BluetoothServerSocket? =
                        bluetoothAdapter?.listenUsingRfcommWithServiceRecord("integrationforlinux", uuid)
                    bluetoothSocket = serverSocket?.accept()
                    manageConnection(bluetoothSocket)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun startClient(device: BluetoothDevice) {
        AsyncTask.execute {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    Log.i("BluetoothClient", "Tentando criar socket de conexão com o dispositivo.")
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)

                    Log.i("BluetoothClient", "Tentando conectar ao dispositivo ${device.name} (${device.address})")
                    bluetoothSocket?.connect()

                    Log.i("BluetoothClient", "Conectado ao dispositivo ${device.name} (${device.address})")
                    manageConnection(bluetoothSocket)
                } catch (e: SecurityException) {
                    Log.e("BluetoothClient", "Erro de segurança ao tentar conectar: ${e.message}", e)
                    // Mostre uma mensagem ao usuário ou solicite a permissão necessária
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Erro de I/O ao tentar conectar: ${e.message}", e)
                    // Tentativa de reconexão ou informar o usuário
                } catch (e: Exception) {
                    Log.e("BluetoothClient", "Erro inesperado ao tentar conectar: ${e.message}", e)
                }
            } else {
                Log.e("BluetoothClient", "Permissão BLUETOOTH_CONNECT não concedida.")
                // Solicitar permissão ou informar o usuário
            }
        }
    }

    private fun manageConnection(socket: BluetoothSocket?) {
        socket?.let {
            try {
                inputStream = it.inputStream
                outputStream = it.outputStream
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun authenticate() {
        // TODO
    }

    fun sendNotification(notificationData: NotificationData) {
        try {
            outputStream?.write(notificationData.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
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

data class NotificationData(val appName: String, val content: String, val icon: ByteArray) {
    fun toByteArray(): ByteArray {
        // TODO
        return byteArrayOf()
    }
}

data class ClipboardData(val content: String)
