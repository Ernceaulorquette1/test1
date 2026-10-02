package cl.driverlink.app.data.firebase

import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.Company
import cl.driverlink.app.domain.model.CompanyDraft
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.CompanyRole
import cl.driverlink.app.domain.model.CompanyStatus
import cl.driverlink.app.domain.model.DutyStatus
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.Membership
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.model.VehicleStatus
import cl.driverlink.app.domain.model.VehicleType
import cl.driverlink.app.domain.model.WorkSession
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.domain.policy.RolePolicy
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.domain.repository.VehicleRepository
import cl.driverlink.app.domain.repository.WorkSessionRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

private fun DocumentSnapshot.toMember(companyId: String): CompanyMember = CompanyMember(
    companyId = companyId,
    userId = str("userId").ifBlank { id },
    displayName = str("displayName"),
    role = enum("role", CompanyRole.DRIVER),
    status = enum("status", MemberStatus.PENDING),
    joinedAt = millis("joinedAt"),
    requestedAt = millis("requestedAt") ?: 0L,
)

private fun DocumentSnapshot.toCompany(inviteCode: String?): Company? {
    if (!exists()) return null
    return Company(
        id = id, rut = str("rut"), legalName = str("legalName"), tradeName = str("tradeName"), email = str("email"),
        phone = str("phone"), address = str("address"), status = enum("status", CompanyStatus.PENDING_REVIEW),
        plan = str("plan"), inviteCode = inviteCode, createdAt = millis("createdAt") ?: 0L,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseCompanyRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : CompanyRepository {

    private fun companyDoc(id: String) = db.collection(Paths.COMPANIES).document(id)

    /**
     * Membresías del usuario mediante collection group `members` filtrado por userId
     * (índice de grupo de colecciones). Una sola cuenta accede a Comunidad y a sus empresas.
     */
    override fun observeMyMemberships(): Flow<List<Membership>> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(emptyList())
        else db.collectionGroup(Paths.MEMBERS).whereEqualTo("userId", state.userId)
            .observe(crash, emptyList()) { snap ->
                snap.documents.mapNotNull { doc -> doc.reference.parent.parent?.id?.let { doc.toMember(it) } }
            }
            .flatMapLatest { members ->
                if (members.isEmpty()) flowOf(emptyList())
                else combine(members.map { member -> observeCompanyFor(member) }) { it.filterNotNull() }
            }
    }

    private fun observeCompanyFor(member: CompanyMember): Flow<Membership?> {
        val companyFlow = companyDoc(member.companyId).observe(crash, null) { it }
        // El código de invitación vive en un documento privado legible solo por administradores.
        val inviteFlow = if (RolePolicy.has(member, CompanyPermission.APPROVE_MEMBERS)) {
            companyDoc(member.companyId).collection(Paths.PRIVATE).document("invite")
                .observe(crash, null) { it.getString("code") }
        } else flowOf(null)
        return combine(companyFlow, inviteFlow) { doc, code -> doc?.toCompany(code)?.let { Membership(it, member) } }
    }

    override fun observeCompany(companyId: String): Flow<Company?> =
        companyDoc(companyId).observe(crash, null) { it.toCompany(null) }

    override suspend fun createCompany(draft: CompanyDraft): AppResult<String> = safeCall(FirebaseErrorMapper) {
        val result = functions.getHttpsCallable("createCompany").call(
            hashMapOf(
                "rut" to draft.rut, "legalName" to draft.legalName, "tradeName" to draft.tradeName,
                "email" to draft.email, "phone" to draft.phone, "address" to draft.address,
            )
        ).await()
        (result.getData() as? Map<*, *>)?.get("companyId")?.toString() ?: throw AppException(AppError.Unknown())
    }

    override suspend fun requestJoin(inviteCode: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        functions.getHttpsCallable("requestJoinCompany").call(hashMapOf("code" to inviteCode)).await()
        Unit
    }

    override fun observeMembers(companyId: String): Flow<List<CompanyMember>> =
        companyDoc(companyId).collection(Paths.MEMBERS)
            .observe(crash, emptyList()) { snap -> snap.documents.map { it.toMember(companyId) } }

    override suspend fun reviewJoinRequest(companyId: String, userId: String, approve: Boolean): AppResult<Unit> =
        safeCall(FirebaseErrorMapper) {
            functions.getHttpsCallable("reviewJoinRequest")
                .call(hashMapOf("companyId" to companyId, "userId" to userId, "approve" to approve)).await()
            Unit
        }

    override suspend fun regenerateInviteCode(companyId: String): AppResult<String> = safeCall(FirebaseErrorMapper) {
        val result = functions.getHttpsCallable("regenerateInviteCode").call(hashMapOf("companyId" to companyId)).await()
        (result.getData() as? Map<*, *>)?.get("code")?.toString() ?: throw AppException(AppError.Unknown())
    }
}

class FirebaseVehicleRepository(
    private val db: FirebaseFirestore,
    private val crash: CrashReporter,
) : VehicleRepository {

    private fun vehicles(companyId: String) =
        db.collection(Paths.COMPANIES).document(companyId).collection(Paths.VEHICLES)

    override fun observeVehicles(companyId: String): Flow<List<Vehicle>> =
        vehicles(companyId).orderBy("plate").observe(crash, emptyList()) { snap ->
            snap.documents.map { d ->
                Vehicle(
                    id = d.id, companyId = companyId, plate = d.str("plate"), brand = d.str("brand"), model = d.str("model"),
                    year = d.int("year"), type = d.enum("type", VehicleType.CAR), status = d.enum("status", VehicleStatus.ACTIVE),
                    assignedDriverId = d.getString("assignedDriverId"), assignedDriverName = d.getString("assignedDriverName"),
                )
            }
        }

    override suspend fun createVehicle(companyId: String, draft: VehicleDraft): AppResult<String> = safeCall(FirebaseErrorMapper) {
        // ID = patente: evita duplicados dentro de la empresa sin consultas adicionales.
        val ref = vehicles(companyId).document(draft.plate)
        ref.set(
            mapOf(
                "plate" to draft.plate, "brand" to draft.brand, "model" to draft.model, "year" to draft.year,
                "type" to draft.type.name, "status" to VehicleStatus.ACTIVE.name,
                "assignedDriverId" to null, "assignedDriverName" to null, "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }

    /** Actualiza el vehículo y registra el historial de asignaciones en un batch atómico. */
    override suspend fun assignVehicle(companyId: String, vehicleId: String, driver: CompanyMember?): AppResult<Unit> =
        safeCall(FirebaseErrorMapper) {
            val company = db.collection(Paths.COMPANIES).document(companyId)
            val batch = db.batch()
            batch.update(
                company.collection(Paths.VEHICLES).document(vehicleId),
                mapOf("assignedDriverId" to driver?.userId, "assignedDriverName" to driver?.displayName,
                    "updatedAt" to FieldValue.serverTimestamp()),
            )
            if (driver != null) {
                batch.set(
                    company.collection(Paths.VEHICLE_ASSIGNMENTS).document(),
                    mapOf("vehicleId" to vehicleId, "driverId" to driver.userId, "assignedAt" to FieldValue.serverTimestamp(), "endedAt" to null),
                )
            }
            batch.commit().await()
            Unit
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseWorkSessionRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : WorkSessionRepository {

    private fun sessions(companyId: String) =
        db.collection(Paths.COMPANIES).document(companyId).collection(Paths.WORK_SESSIONS)

    override fun observeActiveSession(companyId: String): Flow<WorkSession?> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(null)
        else sessions(companyId)
            .whereEqualTo("userId", state.userId)
            .whereEqualTo("endedAt", null)
            .limit(1)
            .observe(crash, null) { snap -> snap.documents.firstOrNull()?.toSession(companyId) }
    }

    override suspend fun startSession(companyId: String, vehicleId: String?): AppResult<String> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        val ref = sessions(companyId).document()
        ref.set(
            mapOf(
                "userId" to uid, "vehicleId" to vehicleId, "status" to DutyStatus.ON_DUTY.name,
                "startedAt" to FieldValue.serverTimestamp(), "endedAt" to null,
            )
        ).await()
        ref.id
    }

    override suspend fun updateStatus(companyId: String, sessionId: String, status: DutyStatus): AppResult<Unit> =
        safeCall(FirebaseErrorMapper) {
            sessions(companyId).document(sessionId).update("status", status.name).await()
            Unit
        }

    override suspend fun endSession(companyId: String, sessionId: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        sessions(companyId).document(sessionId)
            .update(mapOf("status" to DutyStatus.OFF_DUTY.name, "endedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }

    /**
     * Ubicación operacional autorizada: última posición en la jornada + historial
     * (recorrido autorizado) sujeto a la política de retención configurada en servidor.
     */
    override suspend fun pushLocation(companyId: String, sessionId: String, point: GeoPoint): AppResult<Unit> =
        safeCall(FirebaseErrorMapper) {
            val session = sessions(companyId).document(sessionId)
            val batch = db.batch()
            batch.update(session, mapOf("lastLatitude" to point.latitude, "lastLongitude" to point.longitude,
                "lastLocationAt" to FieldValue.serverTimestamp()))
            batch.set(session.collection(Paths.LOCATION_UPDATES).document(),
                mapOf("latitude" to point.latitude, "longitude" to point.longitude, "createdAt" to FieldValue.serverTimestamp()))
            batch.commit().await()
            Unit
        }

    private fun DocumentSnapshot.toSession(companyId: String): WorkSession {
        val lat = getDouble("lastLatitude")
        val lng = getDouble("lastLongitude")
        return WorkSession(
            id = id, companyId = companyId, userId = str("userId"), vehicleId = getString("vehicleId"),
            status = enum("status", DutyStatus.ON_DUTY), startedAt = millis("startedAt") ?: System.currentTimeMillis(),
            endedAt = millis("endedAt"), lastLocation = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            lastLocationAt = millis("lastLocationAt"),
        )
    }
}
