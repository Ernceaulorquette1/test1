package com.pronoscore360.app.push

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pronoscore360.app.PronoApp
import com.pronoscore360.app.R
import com.pronoscore360.app.data.TokenStoreImpl

/** Notificaciones push (FCM). Sólo funciona cuando el proyecto tiene google-services.json (Firebase configurado). */
class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // El registro en el backend se hace al iniciar sesión (MainActivity → SessionViewModel.registerDevice).
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("pending_token", token).apply()
    }
    override fun onMessageReceived(m: RemoteMessage) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(this, PronoApp.CHANNEL_ALERTS).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(m.notification?.title ?: m.data["title"] ?: getString(R.string.app_name))
            .setContentText(m.notification?.body ?: m.data["body"] ?: "").setAutoCancel(true).build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n)
    }
}
