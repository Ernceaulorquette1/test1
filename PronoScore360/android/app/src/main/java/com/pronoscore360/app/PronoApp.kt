package com.pronoscore360.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PronoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    companion object { const val CHANNEL_ALERTS = "match_alerts" }
}
