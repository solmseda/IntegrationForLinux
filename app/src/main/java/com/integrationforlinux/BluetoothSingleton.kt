package com.integrationforlinux

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.lang.ref.WeakReference

object BluetoothSingleton {
    private var initialized = false
    private lateinit var manager: BluetoothConnectionManager
    private var notificationListenerServiceRef: WeakReference<MyNotificationListenerService>? = null

    /**
     * Deve ser chamado em toda Activity/Service que precise de Bluetooth,
     * antes de usar qualquer outro método do singleton.
     */
    fun init(context: Context) {
        if (!initialized) {
            manager = BluetoothConnectionManager(context)
            initialized = true
        }
    }

    /**
     * Inicia apenas o servidor Bluetooth (aguardando conexão do Linux).
     * Não tentamos auto-conectar como cliente, pois queremos que o Linux
     * se conecte ao Android.
     */
    fun startServerAndAutoConnect() {
        // **Apenas inicia o servidor**. Não há “auto-conectar” neste fluxo.
        manager.startServer()
    }

    /**
     * Envia a notificação ao Linux via Bluetooth, se houver uma conexão ativa.
     */
    fun sendNotification(notificationData: NotificationData) {
        manager.sendNotification(notificationData)
    }

    /**
     * Encerra a conexão do cliente (Linux) e o servidor, se chamado.
     */
    fun closeServer() {
        manager.closeClientSocket()
        manager.closeServerSocket()
    }

    fun registerNotificationListenerService(service: MyNotificationListenerService) {
        notificationListenerServiceRef = WeakReference(service)
    }

    fun getNotificationListenerService(): MyNotificationListenerService? {
        return notificationListenerServiceRef?.get()
    }
}
