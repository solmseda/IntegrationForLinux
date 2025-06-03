package com.integrationforlinux

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListenerService : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa o singleton para garantir que o BluetoothConnectionManager exista
        BluetoothSingleton.init(this)
        Log.d("NotificationService", "MyNotificationListenerService criado e BluetoothSingleton iniciado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val pm = applicationContext.packageManager
        var appName = packageName
        try {
            val packageInfo = pm.getPackageInfo(packageName, 0)
            appName = packageInfo.applicationInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val notification = sbn.notification
        val extras = notification.extras
        val content = extras.getCharSequence("android.text")?.toString() ?: "Sem conteúdo"

        val notificationData = NotificationData(
            appName = appName,
            content = content,
            icon = notification.smallIcon
        )

        Log.d("NotificationService", "Notificação recebida de $appName: $content")
        // Envia via singleton (que reusa a mesma instância de BluetoothConnectionManager)
        BluetoothSingleton.sendNotification(notificationData)
    }
}
