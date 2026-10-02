package cl.driverlink.app.services.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.driverlink.app.MainActivity
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.NotificationCategory

/** Publica notificaciones locales. Lo usan FCM (prod) y el simulador (demo). */
interface LocalNotifier {
    fun notify(category: NotificationCategory, title: String, body: String)
}

class NotificationHelper(private val context: Context) : LocalNotifier {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SOS, context.getString(R.string.notif_channel_sos), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_COMMUNITY, context.getString(R.string.notif_channel_community), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_GENERAL, context.getString(R.string.notif_channel_general), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_TRACKING, context.getString(R.string.notif_channel_tracking), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannels(channels)
    }

    override fun notify(category: NotificationCategory, title: String, body: String) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, category.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelFor(category))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (category == NotificationCategory.SOS_UPDATES) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(category.ordinal * 1000 + (title.hashCode() and 0xFFF), notification)
        } catch (e: SecurityException) {
            // Permiso revocado entre la verificación y la publicación: se ignora.
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channelFor(category: NotificationCategory): String = when (category) {
        NotificationCategory.NEARBY_ALERTS -> CHANNEL_ALERTS
        NotificationCategory.SOS_UPDATES -> CHANNEL_SOS
        NotificationCategory.REPLIES, NotificationCategory.CHANNEL_ACTIVITY -> CHANNEL_COMMUNITY
        NotificationCategory.ADMIN, NotificationCategory.SUBSCRIPTION -> CHANNEL_GENERAL
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_SOS = "sos"
        const val CHANNEL_COMMUNITY = "community"
        const val CHANNEL_GENERAL = "general"
        const val CHANNEL_TRACKING = "tracking"
    }
}
