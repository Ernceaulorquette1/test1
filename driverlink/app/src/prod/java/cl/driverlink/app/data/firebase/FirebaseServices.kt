package cl.driverlink.app.data.firebase

import android.content.Context
import android.os.Bundle
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.domain.model.NotificationCategory
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.NotificationPreferencesRepository
import cl.driverlink.app.domain.repository.UserRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

class FirebaseAnalyticsTracker(context: Context) : AnalyticsTracker {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun log(event: AnalyticsEvent, params: Map<String, String>) {
        val bundle = Bundle().apply { params.forEach { (k, v) -> putString(k, v.take(100)) } }
        analytics.logEvent(event.eventName, bundle)
    }

    override fun setUserId(userId: String?) = analytics.setUserId(userId)
}

class CrashlyticsReporter : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    override fun log(message: String) = crashlytics.log(message)
    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)
    override fun setUserId(userId: String?) = crashlytics.setUserId(userId.orEmpty())
}

/**
 * Mantiene el token FCM del dispositivo y las suscripciones a temas según preferencias.
 * Los temas (`alerts_<ciudad>`, `admin`) permiten notificar por zona sin consultar
 * a todos los usuarios, lo que reduce lecturas y costos.
 */
class FcmSync(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val preferences: NotificationPreferencesRepository,
    private val crash: CrashReporter,
) {
    private val messaging = FirebaseMessaging.getInstance()
    private var currentTopics: Set<String> = emptySet()

    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.authState.filterIsInstance<AuthState.SignedIn>().distinctUntilChanged().collect { state ->
                runCatching { registerToken(state.userId, messaging.token.await()) }.onFailure(crash::recordException)
            }
        }
        scope.launch {
            combine(users.observeCurrentUser(), preferences.observePreferences()) { user, prefs ->
                if (user == null) emptySet()
                else buildSet {
                    if (prefs.isEnabled(NotificationCategory.NEARBY_ALERTS) && user.city.isNotBlank()) add("alerts_${slug(user.city)}")
                    if (prefs.isEnabled(NotificationCategory.ADMIN)) add("admin")
                }
            }.distinctUntilChanged().collect { topics -> syncTopics(topics) }
        }
    }

    suspend fun registerToken(uid: String, token: String) {
        db.collection(Paths.USERS).document(uid).collection(Paths.DEVICES).document(token)
            .set(mapOf("platform" to "android", "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun syncTopics(topics: Set<String>) {
        runCatching {
            (currentTopics - topics).forEach { messaging.unsubscribeFromTopic(it).await() }
            (topics - currentTopics).forEach { messaging.subscribeToTopic(it).await() }
            currentTopics = topics
        }.onFailure(crash::recordException)
    }

    companion object {
        /** "Viña del Mar" → "vina_del_mar" (los temas FCM solo aceptan [a-zA-Z0-9-_.~%]). */
        fun slug(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
    }
}
