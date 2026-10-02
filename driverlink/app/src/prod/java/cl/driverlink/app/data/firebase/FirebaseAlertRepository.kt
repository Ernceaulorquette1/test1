package cl.driverlink.app.data.firebase

import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertStatus
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class FirebaseAlertRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val crash: CrashReporter,
) : AlertRepository {

    /**
     * Consulta acotada por ciudad + estado + vigencia (índice compuesto city/status/expiresAt).
     * Nunca se escuchan todas las alertas de Chile.
     */
    override fun observeActiveAlerts(city: String?): Flow<List<Alert>> {
        var query = db.collection(Paths.ALERTS).whereEqualTo("status", AlertStatus.ACTIVE.name)
        if (!city.isNullOrBlank()) query = query.whereEqualTo("city", city)
        return query
            .whereGreaterThan("expiresAt", Timestamp.now())
            .orderBy("expiresAt")
            .limit(MAX_ALERTS)
            .observe(crash, emptyList()) { snap -> snap.documents.mapNotNull { it.toAlert() } }
    }

    override fun observeAlert(alertId: String): Flow<Alert?> =
        db.collection(Paths.ALERTS).document(alertId).observe(crash, null) { it.toAlert() }

    /** El servidor (onAlertCreated) normaliza expiresAt y notifica a la zona. */
    override suspend fun createAlert(draft: AlertDraft, expiresAt: Long): AppResult<String> = safeCall(FirebaseErrorMapper) {
        val user = users.observeCurrentUser().filterNotNull().first()
        val ref = db.collection(Paths.ALERTS).document()
        ref.set(
            mapOf(
                "creatorId" to user.id,
                "creatorName" to user.displayName,
                "category" to draft.category.name,
                "description" to draft.description,
                "latitude" to draft.location.latitude,
                "longitude" to draft.location.longitude,
                "city" to draft.city,
                "commune" to draft.commune,
                "status" to AlertStatus.ACTIVE.name,
                "confirmationsCount" to 0,
                "endedCount" to 0,
                "incorrectCount" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt.toTimestamp(),
            )
        ).await()
        ref.id
    }

    /**
     * El voto se guarda en `alerts/{id}/confirmations/{uid}`: el ID del documento es el uid,
     * así las reglas impiden votos repetidos. Los contadores los actualiza una Cloud Function.
     */
    override suspend fun vote(alertId: String, vote: AlertVote): AppResult<Unit> {
        val result = safeCall(FirebaseErrorMapper) {
            val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
            db.collection(Paths.ALERTS).document(alertId).collection(Paths.CONFIRMATIONS).document(uid)
                .set(mapOf("vote" to vote.name, "userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                .await()
            Unit
        }
        // Un segundo voto se convierte en "update", que las reglas rechazan.
        return if (result is AppResult.Failure && result.error == AppError.PermissionDenied) {
            AppResult.Failure(AppError.AlreadyVoted)
        } else result
    }

    override suspend fun myVote(alertId: String): AppResult<AlertVote?> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        try {
            val doc = db.collection(Paths.ALERTS).document(alertId).collection(Paths.CONFIRMATIONS).document(uid).get().await()
            doc.getString("vote")?.let { v -> AlertVote.entries.firstOrNull { it.name == v } }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) null else throw e
        }
    }

    private fun DocumentSnapshot.toAlert(): Alert? {
        if (!exists()) return null
        val lat = getDouble("latitude") ?: return null
        val lng = getDouble("longitude") ?: return null
        return Alert(
            id = id,
            creatorId = str("creatorId"),
            creatorName = str("creatorName"),
            category = enum("category", AlertCategory.OTHER),
            description = str("description"),
            location = GeoPoint(lat, lng),
            city = str("city"),
            commune = str("commune"),
            status = enum("status", AlertStatus.ACTIVE),
            confirmationsCount = int("confirmationsCount"),
            endedCount = int("endedCount"),
            incorrectCount = int("incorrectCount"),
            createdAt = millis("createdAt") ?: System.currentTimeMillis(),
            expiresAt = millis("expiresAt") ?: 0L,
            updatedAt = millis("updatedAt") ?: 0L,
        )
    }

    private companion object { const val MAX_ALERTS = 100L }
}
