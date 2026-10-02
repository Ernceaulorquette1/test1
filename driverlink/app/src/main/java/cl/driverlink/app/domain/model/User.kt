package cl.driverlink.app.domain.model

/** Plataformas donde trabaja el conductor. Solo texto: sin logos ni marcas de terceros. */
enum class WorkPlatform(val displayName: String) {
    UBER("Uber"),
    DIDI("DiDi"),
    CABIFY("Cabify"),
    OTHER("Otra");

    companion object {
        fun fromId(id: String): WorkPlatform? = entries.firstOrNull { it.name == id }
    }
}

enum class VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED, SUSPENDED }

enum class AccountStatus { ACTIVE, SUSPENDED, DELETED }

/**
 * Rol de plataforma. Se asigna en servidor (custom claims) y nunca desde el cliente.
 * Los roles de empresa (dueño, supervisor, despachador...) viven en [CompanyRole].
 */
enum class PlatformRole { DRIVER, MODERATOR, ADMIN, SOS_OPERATOR }

/** Indicadores de reputación calculados por el servidor (solo lectura en el cliente). */
data class Reputation(
    val alertsCreated: Int = 0,
    val alertsConfirmed: Int = 0,
    val usefulReports: Int = 0,
    val score: Int = 0,
)

/** Perfil privado completo del usuario autenticado. */
data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val photoUrl: String?,
    val city: String,
    val commune: String,
    val platforms: List<WorkPlatform>,
    val verificationStatus: VerificationStatus,
    val accountStatus: AccountStatus,
    val subscriptionStatus: SubscriptionStatus,
    val role: PlatformRole,
    val reputation: Reputation,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val displayName: String get() = "$firstName $lastName".trim()
    val initials: String
        get() = listOf(firstName, lastName)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}

/** Datos públicos de un usuario: nunca incluye correo, teléfono ni documentos. */
data class PublicProfile(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
    val city: String,
    val platforms: List<WorkPlatform>,
    val verificationStatus: VerificationStatus,
    val memberSince: Long,
)

data class RegistrationData(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val password: String,
    val city: String,
    val commune: String,
    val platforms: Set<WorkPlatform>,
    val photoUri: String? = null,
)

/** Campos editables por el propio usuario. El resto lo controla el servidor. */
data class ProfileUpdate(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val city: String,
    val commune: String,
    val platforms: Set<WorkPlatform>,
    val newPhotoUri: String? = null,
)

data class VerificationRequest(
    val userId: String,
    val status: VerificationStatus,
    val notes: String,
    val evidenceCount: Int,
    val submittedAt: Long?,
    val reviewerComment: String?,
)
