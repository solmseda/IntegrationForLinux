package com.integrationforlinux

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListenerService : NotificationListenerService() {

    // Conjunto para rastrear notificações enviadas recentemente (por key)
    private val sentNotificationKeys = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        // Inicializa o singleton e garante que o servidor já esteja aguardando conexão
        BluetoothSingleton.init(applicationContext)
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
            icon = icon
        )

        Log.d("NotificationService", "Notificação recebida de $appName: $content")

        // Envia via Bluetooth usando o singleton
        BluetoothSingleton.sendNotification(notificationData)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Quando uma notificação é removida, liberamos a key para que,
        // caso volte a ser postada, ela possa ser enviada novamente.
        sentNotificationKeys.remove(sbn.key)
    }
}
