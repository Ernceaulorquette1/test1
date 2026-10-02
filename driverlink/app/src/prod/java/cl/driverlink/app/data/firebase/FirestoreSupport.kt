package cl.driverlink.app.data.firebase

import cl.driverlink.app.core.logging.CrashReporter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

/** Rutas de colecciones centralizadas (ver ARCHITECTURE.md → Modelo Firestore). */
object Paths {
    const val USERS = "users"
    const val PUBLIC_PROFILES = "publicProfiles"
    const val VERIFICATION_REQUESTS = "verificationRequests"
    const val CHANNELS = "channels"
    const val MESSAGES = "messages"
    const val ALERTS = "alerts"
    const val CONFIRMATIONS = "confirmations"
    const val SOS_EVENTS = "sosEvents"
    const val EMERGENCY_CONTACTS = "emergencyContacts"
    const val REPORTS = "reports"
    const val BLOCKED_USERS = "blockedUsers"
    const val SUBSCRIPTIONS = "subscriptions"
    const val NOTIFICATION_PREFERENCES = "notificationPreferences"
    const val COMPANIES = "companies"
    const val MEMBERS = "members"
    const val PRIVATE = "private"
    const val VEHICLES = "vehicles"
    const val VEHICLE_ASSIGNMENTS = "vehicleAssignments"
    const val WORK_SESSIONS = "workSessions"
    const val LOCATION_UPDATES = "locationUpdates"
    const val DEVICES = "devices"
}

/** Región de Cloud Functions (Santiago). Debe coincidir con functions/src/config.ts. */
const val FUNCTIONS_REGION = "southamerica-west1"

fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

fun DocumentSnapshot.millis(field: String): Long? =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()

fun DocumentSnapshot.int(field: String): Int = getLong(field)?.toInt() ?: 0

inline fun <reified T : Enum<T>> DocumentSnapshot.enum(field: String, default: T): T =
    getString(field)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

/**
 * Listener de consulta como Flow. Ante un error (p. ej. permiso denegado) registra el
 * problema y emite [fallback] en lugar de propagar la excepción, para no cerrar la app.
 */
fun <T> Query.observe(crash: CrashReporter, fallback: T, map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            crash.recordException(error)
            trySend(fallback)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(map(snapshot))
    }
    awaitClose { registration.remove() }
}

fun <T> DocumentReference.observe(crash: CrashReporter, fallback: T, map: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            crash.recordException(error)
            trySend(fallback)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(map(snapshot))
    }
    awaitClose { registration.remove() }
}
