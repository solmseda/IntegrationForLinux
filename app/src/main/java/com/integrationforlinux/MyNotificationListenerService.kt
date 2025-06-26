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
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val key = sbn.key
        val extras = notification.extras

        val content = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?: extras.getCharSequence("android.text")?.toString()
            ?: "Sem conteúdo"

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?: run {
                val pkg = sbn.packageName
                applicationContext.packageManager
                    .getApplicationLabel(
                        applicationContext.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
            }

        if (sender == "Você") return

        val last = lastNotificationContent[key]
        if (last == content) return
        lastNotificationContent[key] = content

        val data = NotificationData(
            appName = sbn.packageName,
            sender  = sender,
            content = content,
            icon    = notification.smallIcon,
            key     = key
        )
        BluetoothSingleton.sendNotification(data)

        val replyAction = notification.actions
            ?.firstOrNull { it.remoteInputs?.any { ri -> ri.resultKey.isNotEmpty() } == true }

        if (replyAction != null &&
            (sbn.packageName == WHATSAPP_PACKAGE_NAME || sbn.packageName == TELEGRAM_PACKAGE_NAME)
        ) {
            replyActions[key] = replyAction
            Log.d("NotificationService", "Reply action atualizada para key=$key")
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
