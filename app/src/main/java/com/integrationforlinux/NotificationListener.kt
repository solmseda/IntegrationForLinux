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
                icon = icon
            )

            val bluetoothManager = BluetoothConnectionManager(applicationContext)
            bluetoothManager.sendNotification(notificationData)
        }
    }
}
