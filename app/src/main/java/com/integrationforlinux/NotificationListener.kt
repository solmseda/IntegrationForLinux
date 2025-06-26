package com.integrationforlinux

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val appName = it.packageName
            val content = it.notification.tickerText?.toString() ?: "Sem conteúdo"
            val icon = it.notification.smallIcon

            val notificationData = NotificationData(
                appName = appName,
                content = content,
                icon = icon,
                key = sbn.key // Adiciona a chave da notificação
            )

            // Utilize o BluetoothSingleton para enviar a notificação
            // Não crie uma nova instância de BluetoothConnectionManager aqui
            BluetoothSingleton.init(applicationContext) // Garante que o Singleton está inicializado
            BluetoothSingleton.sendNotification(notificationData)
        }
    }
}
