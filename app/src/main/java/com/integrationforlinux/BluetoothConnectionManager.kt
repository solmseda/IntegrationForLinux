package com.integrationforlinux

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothConnectionManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val uuid: UUID = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")

    private val pendingNotifications = mutableListOf<NotificationData>()    // lista de notificações pendentes


    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1
    }

    /**
     * Inicia o servidor Bluetooth uma única vez e aguarda conexão do Linux.
     * Quando receber a conexão, armazena clientSocket + streams.
     */
    fun startServer() {
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
                serverSocket = bluetoothAdapter
                    ?.listenUsingInsecureRfcommWithServiceRecord("integrationforlinux", uuid)

                Log.d("BluetoothConnManager", "Servidor aguardando conexão com UUID: $uuid...")
                clientSocket = serverSocket?.accept()  // bloqueia até o Python conectar

                clientSocket?.let { socket ->
                    Log.d("BluetoothConnManager", "Dispositivo conectado: ${socket.remoteDevice.name} (${socket.remoteDevice.address})")
                    inputStream = socket.inputStream
                    outputStream = socket.outputStream
                    Log.d("BluetoothConnManager", "Conexão agora ESTÁ ATIVA — streams prontos")

                    // Envia pendentes (se houver)
                    synchronized(pendingNotifications) {
                        for (notif in pendingNotifications) {
                            sendNotificationInternal(notif)
                        }
                        pendingNotifications.clear()
                    }

                    // Inicia a escuta por mensagens do Linux (respostas)
                    listenForMessages()
                }
            } catch (e: IOException) {
                Log.e("BluetoothConnManager", "Erro no servidor Bluetooth: ${e.message}")
            }
        }
    }

    /**
     * Lê (em background) qualquer mensagem que o Linux enviar.
     */
    private fun listenForMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(1024)
                while (true) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead < 0) {
                        Log.d("BluetoothConnManager", "Linux desconectou. Encerrando loop.")
                        break
                    }
                    val mensagem = String(buffer, 0, bytesRead)
                    Log.d("BluetoothConnManager", "Mensagem recebida do Linux: $mensagem")

                    // Tenta desserializar a mensagem como uma resposta
                    handleReplyMessage(mensagem)
                }
            } catch (e: IOException) {
                Log.e("BluetoothConnManager", "Erro ao ler mensagens: ${e.message}")
            } finally {
                closeClientSocket()
            }
        }
    }

    /**
     * Converte um Drawable (obtido do Icon) em String Base64 (PNG).
     */
    private fun drawableToBase64(drawable: Drawable): String? {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        outputStream.close()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Serializa o NotificationData em JSON, convertendo `icon: Icon?` para `iconBase64: String?`.
     */
    fun sendNotification(notificationData: NotificationData) {
        try {
            if (outputStream == null) {
                Log.d("BluetoothConnManager", "Nenhuma conexão ativa para envio.")
                return
            }

            // 1) Convertendo Icon? → Drawable? → Base64
            val iconBase64: String? = notificationData.icon?.let { icon ->
                val drawable: Drawable? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        icon.loadDrawable(context)
                    } else {
                        // para APIs antigas, converta de outra forma se necessário
                        null
                    }
                drawable?.let { drawableToBase64(it) }
            }

            // 2) Criar um mapa temporário para serializar
            val payload = mapOf(
                "appName" to notificationData.appName,
                "content" to notificationData.content,
                "iconBase64" to iconBase64,
                "key" to notificationData.key // Inclui a chave da notificação no JSON
            )

            // 3) Serializar com Gson
            val gson = Gson()
            val jsonData = gson.toJson(payload)

            Log.d("BluetoothConnManager", "Enviando JSON para Linux: $jsonData")
            outputStream!!.write(jsonData.toByteArray())
            outputStream!!.flush()
        } catch (e: Exception) {
            Log.e("BluetoothConnManager", "Erro ao enviar notificação: ${e.message}")
        }
    }

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

    fun closeServerSocket() {
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d("BluetoothConnManager", "ServerSocket fechado.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Converte o NotificationData para JSON (com Base64 do Icon) e grava no outputStream.
     * Deve SER CHAMADO apenas se outputStream != null.
     */
    private fun sendNotificationInternal(notificationData: NotificationData) {
        try {
            // 1) Convertendo Icon? → Drawable? → Base64
            val iconBase64: String? = notificationData.icon?.let { icon ->
                val drawable: Drawable? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        icon.loadDrawable(context)
                    } else {
                        null
                    }
                drawable?.let { drawableToBase64(it) }
            }

            // 2) Cria o payload Map
            val payload = mapOf(
                "appName" to notificationData.appName,
                "content" to notificationData.content,
                "iconBase64" to iconBase64
            )

            // 3) Serializa com Gson
            val gson = Gson()
            val jsonData = gson.toJson(payload)

            Log.d("BluetoothConnManager", "Enviando JSON para Linux: $jsonData")
            outputStream!!.write(jsonData.toByteArray())
            outputStream!!.flush()
        } catch (e: Exception) {
            Log.e("BluetoothConnManager", "Erro ao enviar notificação: ${e.message}")
        }
    }

    private fun handleReplyMessage(jsonMessage: String) {
        try {
            val gson = Gson()
            // Define o tipo esperado para desserialização
            val messageType = object : TypeToken<Map<String, String>>() {}.type
            val messageMap: Map<String, String> = gson.fromJson(jsonMessage, messageType)

            val notificationKey = messageMap["key"]
            val replyText = messageMap["reply"]

            if (notificationKey != null && replyText != null) {
                Log.d("BluetoothConnManager", "Tentando responder à notificação: $notificationKey com texto: $replyText")
                // Obter o NotificationListenerService para acessar a ação de resposta
                val notificationListener = BluetoothSingleton.getNotificationListenerService()
                notificationListener?.getReplyAction(notificationKey)?.let { action ->
                    val remoteInputs = action.remoteInputs
                    val intent = Intent()
                    val bundle = Bundle()

                    remoteInputs?.forEach { remoteInput ->
                        bundle.putCharSequence(remoteInput.resultKey, replyText)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                    try {
                        action.intent.send(context, 0, intent)
                        Log.d("BluetoothConnManager", "Resposta enviada para: $notificationKey")
                    } catch (e: PendingIntent.CanceledException) {
                        Log.e("BluetoothConnManager", "Falha ao enviar resposta para $notificationKey: ${e.message}")
                    }
                } ?: run {
                    Log.d("BluetoothConnManager", "Nenhuma ação de resposta encontrada para a chave: $notificationKey")
                }
            } else {
                var logMessage = "Mensagem recebida não é uma resposta válida."
                if (notificationKey == null) {
                    logMessage += " O campo 'key' está faltando."
                }
                if (replyText == null) {
                    logMessage += " O campo 'reply' está faltando."
                }
                Log.d("BluetoothConnManager", logMessage)
            }
        } catch (e: Exception) {
            Log.e("BluetoothConnManager", "Erro ao processar mensagem de resposta JSON: ${e.message}")
            // Não faz nada se a mensagem não for um JSON de resposta esperado
        }
    }
}
