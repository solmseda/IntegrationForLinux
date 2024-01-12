package com.integrationforlinux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var notificationReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val notificationInfo = intent.getStringExtra("notification_event")
                findViewById<TextView>(R.id.notification_info).text = notificationInfo
            }
        }

        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(notificationReceiver, IntentFilter("com.NOTIFICATION_LISTENER"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(notificationReceiver)
    }
}