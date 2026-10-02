package cl.driverlink.app.domain.model

enum class CompanyStatus { PENDING_REVIEW, ACTIVE, SUSPENDED }

enum class CompanyRole { OWNER, ADMIN, SUPERVISOR, DISPATCHER, DRIVER }

enum class MemberStatus { INVITED, PENDING, ACTIVE, SUSPENDED, REMOVED }

data class Company(
    val id: String,
    val rut: String,
    val legalName: String,
    val tradeName: String,
    val email: String,
    val phone: String,
    val address: String,
    val status: CompanyStatus,
    val plan: String,
    val inviteCode: String?,
    val createdAt: Long,
)

data class CompanyDraft(
    val rut: String,
    val legalName: String,
    val tradeName: String,
    val email: String,
    val phone: String,
    val address: String,
)

data class CompanyMember(
    val companyId: String,
    val userId: String,
    val displayName: String,
    val role: CompanyRole,
    val status: MemberStatus,
    val joinedAt: Long?,
    val requestedAt: Long,
)

/** Una empresa a la que pertenece (o solicitó entrar) el usuario actual. */
data class Membership(val company: Company, val member: CompanyMember)

enum class VehicleType { CAR, VAN, TRUCK, MOTORCYCLE, OTHER }

enum class VehicleStatus { ACTIVE, MAINTENANCE, INACTIVE }

data class Vehicle(
    val id: String,
    val companyId: String,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val type: VehicleType,
    val status: VehicleStatus,
    val assignedDriverId: String?,
    val assignedDriverName: String?,
)

data class VehicleDraft(
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val type: VehicleType,
)

data class VehicleAssignment(
    val id: String,
    val companyId: String,
    val vehicleId: String,
    val driverId: String,
    val assignedAt: Long,
    val endedAt: Long?,
)

enum class DutyStatus { OFF_DUTY, ON_DUTY, BREAK, EMERGENCY }

data class WorkSession(
    val id: String,
    val companyId: String,
    val userId: String,
    val vehicleId: String?,
    val status: DutyStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val lastLocation: GeoPoint?,
    val lastLocationAt: Long?,
)
