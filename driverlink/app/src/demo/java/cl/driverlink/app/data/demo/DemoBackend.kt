package cl.driverlink.app.data.demo

import android.content.Context
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertStatus
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.BlockedUser
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.Company
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.NotificationCategory
import cl.driverlink.app.domain.model.NotificationPreferences
import cl.driverlink.app.domain.model.ReportDraft
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleAssignment
import cl.driverlink.app.domain.model.VerificationRequest
import cl.driverlink.app.domain.model.WorkSession
import cl.driverlink.app.services.notifications.LocalNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * "Servidor" en memoria del flavor demo. Replica las reglas que en producción aplican
 * Firestore Rules y Cloud Functions (votos únicos, trial una sola vez, SOS Premium,
 * aprobación de ingreso a empresas...) para que la demo sea fiel al comportamiento real.
 *
 * Los datos se pierden al cerrar la app y nunca tocan Firebase.
 */
class DemoBackend(
    context: Context,
    val clock: Clock,
    val scope: CoroutineScope,
    private val notifier: LocalNotifier,
) {
    val audioDir = File(context.filesDir, "demo_audio")
    val config = MutableStateFlow(AppConfig())

    val currentUserId = MutableStateFlow<String?>(null)
    val passwords = mutableMapOf<String, Pair<String, String>>() // email -> (password, uid)

    val users = MutableStateFlow<Map<String, User>>(emptyMap())
    val verifications = MutableStateFlow<Map<String, VerificationRequest>>(emptyMap())
    val channels = MutableStateFlow<List<Channel>>(emptyList())
    val messages = MutableStateFlow<Map<ChannelKey, List<Message>>>(emptyMap())
    val alerts = MutableStateFlow<Map<String, Alert>>(emptyMap())
    val votes = MutableStateFlow<Map<String, Map<String, AlertVote>>>(emptyMap()) // alertId -> uid -> vote
    val sosEvents = MutableStateFlow<Map<String, SosEvent>>(emptyMap())
    val contacts = MutableStateFlow<Map<String, List<EmergencyContact>>>(emptyMap())
    val subscriptions = MutableStateFlow<Map<String, Subscription>>(emptyMap())
    val blocked = MutableStateFlow<Map<String, List<BlockedUser>>>(emptyMap())
    val reports = MutableStateFlow<List<ReportDraft>>(emptyList())
    val notificationPrefs = MutableStateFlow<Map<String, NotificationPreferences>>(emptyMap())
    val companies = MutableStateFlow<Map<String, Company>>(emptyMap())
    val members = MutableStateFlow<Map<String, Map<String, CompanyMember>>>(emptyMap()) // companyId -> uid -> member
    val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())
    val assignments = MutableStateFlow<List<VehicleAssignment>>(emptyList())
    val workSessions = MutableStateFlow<Map<String, WorkSession>>(emptyMap()) // sessionId -> session

    init {
        DemoSeed.populate(this)
        startSimulation()
    }

    fun requireUid(): String = currentUserId.value ?: throw IllegalStateException("Sin sesión")

    fun currentUser(): User? = currentUserId.value?.let { users.value[it] }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    /** Latencia simulada para que los estados de carga sean visibles en la demo. */
    suspend fun latency() = delay(350)

    fun notify(uid: String, category: NotificationCategory, title: String, body: String) {
        if (uid != currentUserId.value) return
        val prefs = notificationPrefs.value[uid] ?: NotificationPreferences.DEFAULT
        if (prefs.isEnabled(category)) notifier.notify(category, title, body)
    }

    fun addMessage(message: Message) {
        val key = ChannelKey(message.channelId, message.companyId)
        messages.update { it + (key to ((it[key] ?: emptyList()) + message)) }
        channels.update { list -> list.map { if (it.key == key) it.copy(lastMessageAt = message.createdAt) else it } }
    }

    /** Avance automático de un SOS como lo haría un operador real. */
    fun simulateOperator(sosId: String) = scope.launch {
        val steps = listOf(
            5_000L to SosStatus.RECEIVED,
            10_000L to SosStatus.IN_PROGRESS,
            90_000L to SosStatus.RESOLVED,
        )
        for ((wait, status) in steps) {
            delay(wait)
            val current = sosEvents.value[sosId] ?: return@launch
            if (!current.status.isActive) return@launch
            val now = clock.now()
            val updated = current.copy(
                status = status,
                assignedOperatorId = "operador-demo",
                updatedAt = now,
                resolvedAt = if (status == SosStatus.RESOLVED) now else null,
            )
            sosEvents.update { it + (sosId to updated) }
            notify(current.userId, NotificationCategory.SOS_UPDATES, "SOS DriverLink", DemoSeed.sosStatusText(status))
        }
    }

    private fun startSimulation() {
        // Actividad de otros conductores para que la comunidad se sienta viva.
        scope.launch {
            var i = 0
            while (true) {
                delay(45_000)
                if (currentUserId.value != null) {
                    DemoSeed.simulatedMessage(this@DemoBackend, i++)
                }
            }
        }
        scope.launch {
            var i = 0
            while (true) {
                delay(180_000)
                if (currentUserId.value != null) DemoSeed.simulatedAlert(this@DemoBackend, i++)
            }
        }
        // Expiración automática (equivale a la Cloud Function programada expireAlerts).
        scope.launch {
            while (true) {
                delay(60_000)
                val now = clock.now()
                alerts.update { map ->
                    map.mapValues { (_, a) ->
                        if (a.status == AlertStatus.ACTIVE && a.expiresAt <= now) a.copy(status = AlertStatus.EXPIRED, updatedAt = now) else a
                    }
                }
            }
        }
    }
}
