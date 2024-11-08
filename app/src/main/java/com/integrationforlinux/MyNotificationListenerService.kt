package com.integrationforlinux

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.pm.PackageManager

class MyNotificationListenerService : NotificationListenerService() {
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BluetoothConnectionManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
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
        val content = extras.getCharSequence("android.text")?.toString() ?: "Sem conteúdo"

        val notificationData = NotificationData(
            appName = appName,
            content = content,
            icon = notification.smallIcon
        )

        bluetoothConnectionManager.sendNotification(notificationData)
    }
}
