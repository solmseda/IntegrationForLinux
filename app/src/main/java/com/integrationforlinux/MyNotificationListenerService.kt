package com.integrationforlinux

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.content.pm.PackageManager
import com.google.gson.Gson

class MyNotificationListenerService : NotificationListenerService() {
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BluetoothConnectionManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Extrair informações da notificação
        val packageName = sbn.packageName
        val pm = applicationContext.packageManager
        var appName = ""

        try {
            val packageInfo = pm.getPackageInfo(packageName, 0)
            appName = packageInfo.applicationInfo.loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val notification = sbn.notification
        val extras = notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: "Sem conteúdo"

        val notificationData = NotificationData(
            appName = appName,
            content = text,
            icon = notification.smallIcon
        )

        // Converte os dados da notificação em JSON para enviar via Bluetooth
        bluetoothConnectionManager.sendNotification(notificationData)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Lidar com a remoção da notificação, se necessário
    }
}
