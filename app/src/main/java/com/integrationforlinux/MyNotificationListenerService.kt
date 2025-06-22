package com.integrationforlinux

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListenerService : NotificationListenerService() {

    // Conjunto para rastrear notificações enviadas recentemente (por key)
    private val sentNotificationKeys = mutableSetOf<String>()
    private val replyActions = mutableMapOf<String, Notification.Action>()

    companion object {
        private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
        private const val TELEGRAM_PACKAGE_NAME = "org.telegram.messenger"
    }

    override fun onCreate() {
        super.onCreate()
        // Inicializa o singleton e garante que o servidor já esteja aguardando conexão
        BluetoothSingleton.init(applicationContext)
        BluetoothSingleton.registerNotificationListenerService(this) // Registra a instância atual
        BluetoothSingleton.startServerAndAutoConnect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Obtém a "key" única dessa notificação (inclui packageName + id + tag)
        val notificationKey = sbn.key

        // Se já enviamos essa mesma key, ignoramos para evitar duplicata
        if (sentNotificationKeys.contains(notificationKey)) {
            return
        }

        // Marca como enviada
        sentNotificationKeys.add(notificationKey)

        // Extrai o nome legível do app (fallback para packageName)
        val packageName = sbn.packageName
        val pm = applicationContext.packageManager
        var appName = packageName
        try {
            val packageInfo = pm.getPackageInfo(packageName, 0)
            appName = packageInfo.applicationInfo.loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // Obtém o conteúdo principal da notificação
        val notification = sbn.notification
        val extras = notification.extras
        val content = extras.getCharSequence("android.text")?.toString() ?: "Sem conteúdo"

        // Obtém o ícone (pode ser null)
        val icon = notification.smallIcon

        // Monta o objeto NotificationData
        val notificationData = NotificationData(
            appName = appName,
            content = content,
            icon = icon,
            key = notificationKey // Adiciona a chave da notificação para referência futura
        )

        Log.d("NotificationService", "Notificação recebida de $appName: $content")

        // Envia via Bluetooth usando o singleton
        BluetoothSingleton.sendNotification(notificationData)

        // Verifica se a notificação é do WhatsApp ou Telegram e armazena a ação de resposta
        if (packageName == WHATSAPP_PACKAGE_NAME || packageName == TELEGRAM_PACKAGE_NAME) {
            notification.actions?.find { action ->
                action.remoteInputs?.any { remoteInput ->
                    remoteInput.resultKey.isNotEmpty() // Verifica se existe um RemoteInput para resposta
                } == true
            }?.let { replyAction ->
                replyActions[notificationKey] = replyAction
                Log.d("NotificationService", "Ação de resposta armazenada para a notificação: $notificationKey")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Quando uma notificação é removida, liberamos a key para que,
        // caso volte a ser postada, ela possa ser enviada novamente.
        sentNotificationKeys.remove(sbn.key)
        replyActions.remove(sbn.key) // Remove também a ação de resposta associada
        Log.d("NotificationService", "Ação de resposta removida para a notificação: ${sbn.key}")
    }

    fun getReplyAction(notificationKey: String): Notification.Action? {
        return replyActions[notificationKey]
    }
}
