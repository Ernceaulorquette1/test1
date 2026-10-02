package cl.driverlink.app.data.firebase

import cl.driverlink.app.DriverLinkApp
import cl.driverlink.app.domain.model.NotificationCategory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe notificaciones FCM. El servidor envía mensajes de datos con
 * `category`, `title` y `body`; el cliente decide el canal de notificación.
 */
class DriverLinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection(Paths.USERS).document(uid)
            .collection(Paths.DEVICES).document(token)
            .set(mapOf("platform" to "android", "updatedAt" to FieldValue.serverTimestamp()))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val category = NotificationCategory.entries.firstOrNull { it.name == data["category"] } ?: NotificationCategory.ADMIN
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        (application as DriverLinkApp).container.notificationHelper.notify(category, title, body)
    }
}
