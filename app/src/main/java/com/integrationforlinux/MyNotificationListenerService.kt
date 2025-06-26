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
    private val sentNotificationKeys = mutableSetOf<String>()
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
        // 1) Obtém a key única (packageName + id + tag)
        val key = sbn.key

        // 2) Ignora duplicatas de notificações já enviadas
        if (!sentNotificationKeys.add(key)) {
            return
        }

        // 3) Resolve nome legível do app (fallback para packageName)
        val packageName = sbn.packageName
        val appName = try {
            val pm = applicationContext.packageManager
            pm.getPackageInfo(packageName, 0)
                .applicationInfo
                .loadLabel(pm)
                .toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        // 4) Extrai conteúdo e sender (título da notificação)
        val notification = sbn.notification
        val extras = notification.extras
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?: extras.getCharSequence("android.text")
                ?.toString()
            ?: "Sem conteúdo"
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?: appName

        // 5) Monta objeto NotificationData com sender opcional
        val data = NotificationData(
            appName = appName,
            sender  = sender,
            content = content,
            icon    = notification.smallIcon,
            key     = key
        )

        // Serializa para JSON
        val gson = Gson()
        val jsonData = gson.toJson(data)

        // **DEBUG**: imprime todo o payload no logcat
        Log.d("NotificationService", "Payload completo para Linux: $jsonData")

        Log.d("NotificationService", "Notificação recebida de $sender: $content")

        // 6) Envia via Bluetooth
        BluetoothSingleton.sendNotification(data)

        // 7) Se app de chat, armazena ação de resposta
        if (packageName == WHATSAPP_PACKAGE_NAME || packageName == TELEGRAM_PACKAGE_NAME) {
            notification.actions
                ?.firstOrNull { action ->
                    action.remoteInputs
                        ?.any { it.resultKey.isNotEmpty() }
                        ?: false
                }
                ?.let { act ->
                    replyActions[key] = act
                    Log.d("NotificationService", "Reply action salva para $key")
                }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Quando a notificação some, limpa key e ação de reply
        val key = sbn.key
        sentNotificationKeys.remove(key)
        replyActions.remove(key)
        Log.d("NotificationService", "Notificação removida, key liberada: $key")
    }

    // Método para obter ação de reply armazenada
    fun getReplyAction(notificationKey: String): Notification.Action? {
        return replyActions[notificationKey]
    }
}
