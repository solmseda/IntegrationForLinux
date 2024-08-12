package com.integrationforlinux
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManager {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val UUID = UUID.fromString("YOUR_UUID_STRING_HERE")

    fun startServer() {
        val serverSocket: BluetoothServerSocket? =
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord("YourAppName", UUID)
        bluetoothSocket = serverSocket?.accept()
        manageConnection(bluetoothSocket)
    }

    fun startClient(device: BluetoothDevice) {
        bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID)
        bluetoothSocket?.connect()
        manageConnection(bluetoothSocket)
    }

    private fun manageConnection(socket: BluetoothSocket?) {
        socket?.also {
            inputStream = it.inputStream
            outputStream = it.outputStream
        }
    }

    fun authenticate() {
        // TODO
    }

    fun sendNotification(notificationData: NotificationData) {
        outputStream?.write(notificationData.toByteArray())
    }

    fun receiveClipboardData(): ClipboardData? {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        val data = StringBuilder()
        while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
            data.append(String(buffer, 0, bytesRead))
        }
        return ClipboardData(data.toString())
    }

    fun closeConnection() {
        inputStream?.close()
        outputStream?.close()
        bluetoothSocket?.close()
    }
}

data class NotificationData(val appName: String, val content: String, val icon: ByteArray) {
    fun toByteArray(): ByteArray {
        // TODO
        return byteArrayOf()
    }
}

data class ClipboardData(val content: String)
