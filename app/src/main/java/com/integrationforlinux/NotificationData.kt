package com.integrationforlinux

import android.graphics.drawable.Icon

data class NotificationData(
    val appName: String,
    val content: String,
    val icon: Icon?,
    val key: String // Adiciona o campo para a chave da notificação
)
