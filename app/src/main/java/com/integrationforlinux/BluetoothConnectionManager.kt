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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null

    private var clientSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val uuid: UUID = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1
    }

    /**
     * Inicia o servidor Bluetooth uma única vez e aguarda conexão do Linux.
     * Quando a conexão chega, salva o clientSocket + streams e NÃO fecha imediatamente.
     */
    fun startServer() {
        // Se já tiver um serverSocket aberto, não crie outro
        if (serverSocket != null) return

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
            try {
                // Cria o serverSocket apenas uma vez
                serverSocket = bluetoothAdapter
                    ?.listenUsingInsecureRfcommWithServiceRecord("integrationforlinux", uuid)

                Log.d("BluetoothConnManager", "Servidor aguardando conexão com UUID: $uuid...")
                // Bloqueia até que o Linux conecte
                clientSocket = serverSocket?.accept()

                clientSocket?.let { socket ->
                    Log.d("BluetoothConnManager", "Dispositivo conectado: ${socket.remoteDevice.name} (${socket.remoteDevice.address})")

                    // **Armazena o input/output streams para enviar notificações depois**
                    inputStream = socket.inputStream
                    outputStream = socket.outputStream

                    Log.d("BluetoothConnManager", "Conexão gerenciada com sucesso — streams prontos")

                    listenForMessages()
                }
            } catch (e: IOException) {
                Log.e("BluetoothConnManager", "Erro no servidor Bluetooth: ${e.message}")
            }
        }
    }

    /**
     * Fica lendo (em background) qualquer mensagem que o Linux enviar.
     * (Aqui podemos logar ou exibir no Android, se quisermos.)
     */
    private fun listenForMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(1024)
                while (true) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead < 0) {
                        Log.d("BluetoothConnManager", "O Linux desconectou. Encerrando loop de leitura.")
                        break
                    }
                    val mensagem = String(buffer, 0, bytesRead)
                    Log.d("BluetoothConnManager", "Mensagem recebida do Linux: $mensagem")
                }
            } catch (e: IOException) {
                Log.e("BluetoothConnManager", "Erro ao ler mensagens: ${e.message}")
            } finally {
                // Quando o loop termina (Linux desconectou), fecha o socket
                closeClientSocket()
            }
        }
    }

    /**
     * Envia a NotificationData (como JSON) para o Linux, usando o clientSocket que está ativo.
     * Se outputStream for nulo, significa que ainda não houve conexão do Linux → Android.
     */
    fun sendNotification(notificationData: NotificationData) {
        try {
            if (outputStream == null) {
                Log.d("BluetoothConnManager", "Nenhuma conexão ativa com Linux para envio.")
                return
            }
            val gson = Gson()
            val jsonData = gson.toJson(notificationData)
            Log.d("BluetoothConnManager", "Enviando JSON para Linux: $jsonData")
            outputStream!!.write(jsonData.toByteArray())
            outputStream!!.flush()
        } catch (e: Exception) {
            Log.e("BluetoothConnManager", "Erro ao enviar notificação: ${e.message}")
        }
    }

    /**
     * Se quisermos desconectar o Linux manualmente (ou no onDestroy do app), fechamos esse socket.
     */
    fun closeClientSocket() {
        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            inputStream = null
            outputStream = null
            clientSocket = null
            Log.d("BluetoothConnManager", "ClientSocket fechado.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Fecha o servidor completamente (também no onDestroy, se desejado).
     */
    fun closeServerSocket() {
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d("BluetoothConnManager", "ServerSocket fechado.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
