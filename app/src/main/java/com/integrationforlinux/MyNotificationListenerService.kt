package com.integrationforlinux

import android.app.Notification
import android.app.RemoteInput
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson

class MyNotificationListenerService : NotificationListenerService() {

    // Conjunto para rastrear notificações enviadas recentemente
    private val lastNotificationContent = mutableMapOf<String, String>()
    // Armazena ações de reply para notificações de chat
    private val replyActions = mutableMapOf<String, Notification.Action>()

    companion object {
        private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
        private const val TELEGRAM_PACKAGE_NAME = "org.telegram.messenger"
    }

    override fun onCreate() {
        super.onCreate()
        // Inicializa o singleton e mantém o servidor Bluetooth ativo
        BluetoothSingleton.init(applicationContext)
        BluetoothSingleton.registerNotificationListenerService(this)
        BluetoothSingleton.startServerAndAutoConnect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1) Key única da notificação
        val key = sbn.key

        // 2) Extrai informação de texto e sender
        val notification = sbn.notification
        val extras = notification.extras

        val content = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?: extras.getCharSequence("android.text")
                ?.toString()
            ?: "Sem conteúdo"

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?: run {
                // fallback: nome do app
                val pkg = sbn.packageName
                applicationContext.packageManager
                    .getApplicationLabel(
                        applicationContext.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
            }

        // 3) Verifica se é novo conteúdo para esta key
        val last = lastNotificationContent[key]
        if (last == content) {
            // mesmo texto da última vez: ignora
            return
        }
        // armazena o conteúdo atual
        lastNotificationContent[key] = content

        // 4) Constrói o NotificationData (já com sender)
        val data = NotificationData(
            appName = sbn.packageName,
            sender  = sender,
            content = content,
            icon    = notification.smallIcon,
            key     = key
        )

        Log.d("NotificationService", "Notificação atualizada de $sender: $content")

        // 5) Envia via Bluetooth
        BluetoothSingleton.sendNotification(data)

        // 6) Se for chat, armazena ação de reply (mesmo código de antes)
        if (sbn.packageName == WHATSAPP_PACKAGE_NAME || sbn.packageName == TELEGRAM_PACKAGE_NAME) {
            notification.actions
                ?.firstOrNull { it.remoteInputs?.any { ri -> ri.resultKey.isNotEmpty() } == true }
                ?.let { act ->
                    replyActions[key] = act
                    Log.d("NotificationService", "Reply action atualizada para key=$key")
                }
        }
    }


    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = sbn.key
        lastNotificationContent.remove(key)
        replyActions.remove(key)
        Log.d("NotificationService", "Notificação removida, limpar estado para key=$key")
    }

    // Método para obter ação de reply armazenada
    fun getReplyAction(notificationKey: String): Notification.Action? {
        return replyActions[notificationKey]
    }
}
