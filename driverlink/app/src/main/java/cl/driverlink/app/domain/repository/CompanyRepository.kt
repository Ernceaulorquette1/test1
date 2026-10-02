package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.Company
import cl.driverlink.app.domain.model.CompanyDraft
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.DutyStatus
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.Membership
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.model.WorkSession
import kotlinx.coroutines.flow.Flow

interface CompanyRepository {
    fun observeMyMemberships(): Flow<List<Membership>>
    fun observeCompany(companyId: String): Flow<Company?>
    /** Crea la empresa en servidor; el creador queda como OWNER. */
    suspend fun createCompany(draft: CompanyDraft): AppResult<String>
    /** Solicita ingreso con un código. Queda PENDING hasta que un administrador apruebe. */
    suspend fun requestJoin(inviteCode: String): AppResult<Unit>
    fun observeMembers(companyId: String): Flow<List<CompanyMember>>
    suspend fun reviewJoinRequest(companyId: String, userId: String, approve: Boolean): AppResult<Unit>
    suspend fun regenerateInviteCode(companyId: String): AppResult<String>
}

interface VehicleRepository {
    fun observeVehicles(companyId: String): Flow<List<Vehicle>>
    suspend fun createVehicle(companyId: String, draft: VehicleDraft): AppResult<String>
    suspend fun assignVehicle(companyId: String, vehicleId: String, driver: CompanyMember?): AppResult<Unit>
}

interface WorkSessionRepository {
    fun observeActiveSession(companyId: String): Flow<WorkSession?>
    suspend fun startSession(companyId: String, vehicleId: String?): AppResult<String>
    suspend fun updateStatus(companyId: String, sessionId: String, status: DutyStatus): AppResult<Unit>
    suspend fun endSession(companyId: String, sessionId: String): AppResult<Unit>
    /** Ubicación operacional autorizada, solo durante una jornada activa. */
    suspend fun pushLocation(companyId: String, sessionId: String, point: GeoPoint): AppResult<Unit>
}
