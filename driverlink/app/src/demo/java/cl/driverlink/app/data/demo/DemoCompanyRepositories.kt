package cl.driverlink.app.data.demo

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.ChannelType
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
import cl.driverlink.app.domain.model.VehicleAssignment
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.model.VehicleStatus
import cl.driverlink.app.domain.model.WorkSession
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.domain.policy.RolePolicy
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.domain.repository.VehicleRepository
import cl.driverlink.app.domain.repository.WorkSessionRepository
import cl.driverlink.app.domain.validation.InviteCodeGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

private fun DemoBackend.member(companyId: String, uid: String?): CompanyMember? =
    uid?.let { members.value[companyId]?.get(it) }

class DemoCompanyRepository(private val b: DemoBackend) : CompanyRepository {

    override fun observeMyMemberships(): Flow<List<Membership>> =
        combine(b.currentUserId, b.companies, b.members) { uid, companies, members ->
            if (uid == null) return@combine emptyList()
            members.mapNotNull { (companyId, byUser) ->
                val member = byUser[uid] ?: return@mapNotNull null
                val company = companies[companyId] ?: return@mapNotNull null
                // Un miembro no aprobado no ve el código de invitación ni datos internos.
                val visible = if (RolePolicy.has(member, CompanyPermission.APPROVE_MEMBERS)) company else company.copy(inviteCode = null)
                Membership(visible, member)
            }
        }

    override fun observeCompany(companyId: String): Flow<Company?> =
        combine(b.companies, b.members, b.currentUserId) { companies, members, uid ->
            companies[companyId]?.takeIf { members[companyId]?.get(uid)?.status == MemberStatus.ACTIVE }
        }

    override suspend fun createCompany(draft: CompanyDraft): AppResult<String> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        if (b.companies.value.values.any { it.rut == draft.rut }) return AppResult.Failure(AppError.AlreadyExists)
        val id = "co-" + b.newId()
        val now = b.clock.now()
        val company = Company(id, draft.rut, draft.legalName, draft.tradeName, draft.email, draft.phone, draft.address,
            CompanyStatus.ACTIVE, "FLOTAS_BASE", InviteCodeGenerator.generate(), now)
        b.companies.update { it + (id to company) }
        b.members.update {
            it + (id to mapOf(user.id to CompanyMember(id, user.id, user.displayName, CompanyRole.OWNER, MemberStatus.ACTIVE, now, now)))
        }
        // Canales privados por defecto.
        b.channels.update { list ->
            list + listOf("General", "Despacho", "Conductores", "Emergencias").mapIndexed { i, name ->
                Channel("$id-ch-$i", name, "", ChannelType.PRIVATE_COMPANY, ChannelMode.TEXT, "", "", null, id, i, true, null)
            } + Channel("$id-radio", "Radio General", "", ChannelType.PRIVATE_COMPANY, ChannelMode.RADIO, "", "", null, id, 0, true, null)
        }
        return AppResult.Success(id)
    }

    override suspend fun requestJoin(inviteCode: String): AppResult<Unit> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        val company = b.companies.value.values.firstOrNull { it.inviteCode == inviteCode }
            ?: return AppResult.Failure(AppError.NotFound)
        val existing = b.member(company.id, user.id)
        if (existing != null && existing.status != MemberStatus.REMOVED) return AppResult.Failure(AppError.AlreadyExists)
        // Conocer el código NO da acceso: queda PENDING hasta aprobación de un administrador.
        val member = CompanyMember(company.id, user.id, user.displayName, CompanyRole.DRIVER, MemberStatus.PENDING, null, b.clock.now())
        b.members.update { it + (company.id to ((it[company.id] ?: emptyMap()) + (user.id to member))) }
        return AppResult.Success(Unit)
    }

    override fun observeMembers(companyId: String): Flow<List<CompanyMember>> =
        combine(b.members, b.currentUserId) { members, uid ->
            val me = members[companyId]?.get(uid)
            if (RolePolicy.has(me, CompanyPermission.VIEW_MEMBERS) || RolePolicy.has(me, CompanyPermission.START_WORK_SESSION)) {
                members[companyId]?.values?.toList() ?: emptyList()
            } else emptyList()
        }

    override suspend fun reviewJoinRequest(companyId: String, userId: String, approve: Boolean): AppResult<Unit> {
        val me = b.member(companyId, b.currentUserId.value)
        if (!RolePolicy.has(me, CompanyPermission.APPROVE_MEMBERS)) return AppResult.Failure(AppError.PermissionDenied)
        b.latency()
        val now = b.clock.now()
        b.members.update { all ->
            val byUser = all[companyId] ?: return@update all
            val target = byUser[userId] ?: return@update all
            val updated = if (approve) target.copy(status = MemberStatus.ACTIVE, joinedAt = now) else target.copy(status = MemberStatus.REMOVED)
            all + (companyId to (byUser + (userId to updated)))
        }
        return AppResult.Success(Unit)
    }

    override suspend fun regenerateInviteCode(companyId: String): AppResult<String> {
        val me = b.member(companyId, b.currentUserId.value)
        if (!RolePolicy.has(me, CompanyPermission.APPROVE_MEMBERS)) return AppResult.Failure(AppError.PermissionDenied)
        val code = InviteCodeGenerator.generate()
        b.companies.update { map -> map[companyId]?.let { map + (companyId to it.copy(inviteCode = code)) } ?: map }
        return AppResult.Success(code)
    }
}

class DemoVehicleRepository(private val b: DemoBackend) : VehicleRepository {

    override fun observeVehicles(companyId: String): Flow<List<Vehicle>> =
        combine(b.vehicles, b.members, b.currentUserId) { vehicles, members, uid ->
            if (members[companyId]?.get(uid)?.status == MemberStatus.ACTIVE) vehicles[companyId] ?: emptyList() else emptyList()
        }

    override suspend fun createVehicle(companyId: String, draft: VehicleDraft): AppResult<String> {
        val me = b.member(companyId, b.currentUserId.value)
        if (!RolePolicy.has(me, CompanyPermission.MANAGE_VEHICLES)) return AppResult.Failure(AppError.PermissionDenied)
        if (b.vehicles.value[companyId]?.any { it.plate == draft.plate } == true) return AppResult.Failure(AppError.AlreadyExists)
        b.latency()
        val id = "v-" + b.newId()
        val vehicle = Vehicle(id, companyId, draft.plate, draft.brand, draft.model, draft.year, draft.type, VehicleStatus.ACTIVE, null, null)
        b.vehicles.update { it + (companyId to ((it[companyId] ?: emptyList()) + vehicle)) }
        return AppResult.Success(id)
    }

    override suspend fun assignVehicle(companyId: String, vehicleId: String, driver: CompanyMember?): AppResult<Unit> {
        val me = b.member(companyId, b.currentUserId.value)
        if (!RolePolicy.has(me, CompanyPermission.ASSIGN_VEHICLES)) return AppResult.Failure(AppError.PermissionDenied)
        val now = b.clock.now()
        b.vehicles.update { all ->
            all + (companyId to (all[companyId] ?: emptyList()).map {
                if (it.id == vehicleId) it.copy(assignedDriverId = driver?.userId, assignedDriverName = driver?.displayName) else it
            })
        }
        // Historial de asignaciones: se cierra la anterior y se abre una nueva.
        b.assignments.update { list ->
            val closed = list.map { if (it.vehicleId == vehicleId && it.endedAt == null) it.copy(endedAt = now) else it }
            if (driver == null) closed else closed + VehicleAssignment(b.newId(), companyId, vehicleId, driver.userId, now, null)
        }
        return AppResult.Success(Unit)
    }
}

class DemoWorkSessionRepository(private val b: DemoBackend) : WorkSessionRepository {

    override fun observeActiveSession(companyId: String): Flow<WorkSession?> =
        combine(b.workSessions, b.currentUserId) { sessions, uid ->
            sessions.values.firstOrNull { it.companyId == companyId && it.userId == uid && it.endedAt == null }
        }

    override suspend fun startSession(companyId: String, vehicleId: String?): AppResult<String> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        if (!RolePolicy.has(b.member(companyId, uid), CompanyPermission.START_WORK_SESSION)) {
            return AppResult.Failure(AppError.PermissionDenied)
        }
        b.workSessions.value.values.firstOrNull { it.companyId == companyId && it.userId == uid && it.endedAt == null }
            ?.let { return AppResult.Success(it.id) }
        b.latency()
        val id = "ws-" + b.newId()
        b.workSessions.update {
            it + (id to WorkSession(id, companyId, uid, vehicleId, DutyStatus.ON_DUTY, b.clock.now(), null, null, null))
        }
        return AppResult.Success(id)
    }

    override suspend fun updateStatus(companyId: String, sessionId: String, status: DutyStatus): AppResult<Unit> =
        mutate(sessionId) { it.copy(status = status) }

    override suspend fun endSession(companyId: String, sessionId: String): AppResult<Unit> =
        mutate(sessionId) { it.copy(status = DutyStatus.OFF_DUTY, endedAt = b.clock.now()) }

    override suspend fun pushLocation(companyId: String, sessionId: String, point: GeoPoint): AppResult<Unit> =
        mutate(sessionId) { if (it.endedAt == null) it.copy(lastLocation = point, lastLocationAt = b.clock.now()) else it }

    private fun mutate(sessionId: String, transform: (WorkSession) -> WorkSession): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val session = b.workSessions.value[sessionId] ?: return AppResult.Failure(AppError.NotFound)
        if (session.userId != uid) return AppResult.Failure(AppError.PermissionDenied)
        b.workSessions.update { it + (sessionId to transform(session)) }
        return AppResult.Success(Unit)
    }
}
