package cl.driverlink.app.data.firebase

import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.EmergencyContactRepository
import cl.driverlink.app.domain.repository.SosOperatorRepository
import cl.driverlink.app.domain.repository.SosRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

private val ACTIVE_STATUSES = listOf(SosStatus.CREATED.name, SosStatus.RECEIVED.name, SosStatus.IN_PROGRESS.name)

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseSosRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : SosRepository, SosOperatorRepository {

    /** Crea el SOS vía Cloud Function, que valida Premium y notifica a operadores. */
    override suspend fun createSos(type: SosType, location: GeoPoint?, notes: String): AppResult<String> = safeCall(FirebaseErrorMapper) {
        val payload = hashMapOf<String, Any?>(
            "type" to type.name,
            "notes" to notes,
            "latitude" to location?.latitude,
            "longitude" to location?.longitude,
        )
        val result = functions.getHttpsCallable("createSosEvent").call(payload).await()
        (result.getData() as? Map<*, *>)?.get("id")?.toString() ?: throw AppException(AppError.Unknown())
    }

    override fun observeSos(sosId: String): Flow<SosEvent?> =
        db.collection(Paths.SOS_EVENTS).document(sosId).observe(crash, null) { it.toSos() }

    override fun observeMyActiveSos(): Flow<SosEvent?> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(null)
        else db.collection(Paths.SOS_EVENTS)
            .whereEqualTo("userId", state.userId)
            .whereIn("status", ACTIVE_STATUSES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .observe(crash, null) { snap -> snap.documents.firstOrNull()?.toSos() }
    }

    override suspend fun cancelSos(sosId: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        functions.getHttpsCallable("cancelSosEvent").call(hashMapOf("id" to sosId)).await()
        Unit
    }

    /** Las reglas permiten al dueño actualizar SOLO su ubicación mientras el SOS está activo. */
    override suspend fun pushLocation(sosId: String, point: GeoPoint): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        db.collection(Paths.SOS_EVENTS).document(sosId).update(
            mapOf(
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "locationUpdatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }

    // --- Operador SOS (rol SOS_OPERATOR, validado en servidor) ---
    override fun observeActiveCases(): Flow<List<SosEvent>> =
        db.collection(Paths.SOS_EVENTS)
            .whereIn("status", ACTIVE_STATUSES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .observe(crash, emptyList()) { snap -> snap.documents.mapNotNull { it.toSos() } }

    override suspend fun acceptCase(sosId: String): AppResult<Unit> = operatorCall(sosId, "accept")
    override suspend fun updateStatus(sosId: String, status: SosStatus): AppResult<Unit> = operatorCall(sosId, "status", status.name)
    override suspend fun addNote(sosId: String, note: String): AppResult<Unit> = operatorCall(sosId, "note", note)

    private suspend fun operatorCall(sosId: String, action: String, value: String? = null): AppResult<Unit> =
        safeCall(FirebaseErrorMapper) {
            functions.getHttpsCallable("updateSosEvent")
                .call(hashMapOf("id" to sosId, "action" to action, "value" to value)).await()
            Unit
        }

    private fun DocumentSnapshot.toSos(): SosEvent? {
        if (!exists()) return null
        val lat = getDouble("latitude")
        val lng = getDouble("longitude")
        return SosEvent(
            id = id,
            userId = str("userId"),
            userName = str("userName"),
            userPhone = str("userPhone"),
            type = enum("type", SosType.OTHER),
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            status = enum("status", SosStatus.CREATED),
            notes = str("notes"),
            assignedOperatorId = getString("assignedOperatorId"),
            createdAt = millis("createdAt") ?: System.currentTimeMillis(),
            updatedAt = millis("updatedAt") ?: 0L,
            resolvedAt = millis("resolvedAt"),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseEmergencyContactRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : EmergencyContactRepository {

    private fun collection(uid: String) =
        db.collection(Paths.USERS).document(uid).collection(Paths.EMERGENCY_CONTACTS)

    override fun observeContacts(): Flow<List<EmergencyContact>> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(emptyList())
        else collection(state.userId).observe(crash, emptyList()) { snap ->
            snap.documents.map { EmergencyContact(it.id, it.str("name"), it.str("relationship"), it.str("phone")) }
        }
    }

    /** Las reglas exigen suscripción Premium vigente (leída desde `subscriptions/{uid}`). */
    override suspend fun saveContact(contact: EmergencyContact): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        collection(uid).document(contact.id).set(
            mapOf("name" to contact.name, "relationship" to contact.relationship, "phone" to contact.phone,
                "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    override suspend fun deleteContact(contactId: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        collection(uid).document(contactId).delete().await()
        Unit
    }
}
