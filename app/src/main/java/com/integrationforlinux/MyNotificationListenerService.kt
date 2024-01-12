package com.integrationforlinux

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.content.pm.PackageManager

class MyNotificationListenerService : NotificationListenerService() {
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
        val text = extras.getCharSequence("android.text").toString()

        val intent = Intent("com.example.NOTIFICATION_LISTENER")
        intent.putExtra("notification_event", "onNotificationPosted: " + appName + " - " + text + "\n")
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Aqui você pode lidar com a remoção da notificação
    }
}