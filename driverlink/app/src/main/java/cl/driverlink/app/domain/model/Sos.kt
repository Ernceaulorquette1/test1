package cl.driverlink.app.domain.model

enum class SosType { ROBBERY, ACCIDENT, PASSENGER_ISSUE, MECHANICAL, MEDICAL, OTHER }

enum class SosStatus {
    CREATED, RECEIVED, IN_PROGRESS, RESOLVED, CANCELLED;

    val isActive: Boolean get() = this == CREATED || this == RECEIVED || this == IN_PROGRESS
}

data class SosEvent(
    val id: String,
    val userId: String,
    val userName: String,
    val userPhone: String,
    val type: SosType,
    val location: GeoPoint?,
    val status: SosStatus,
    val notes: String,
    val assignedOperatorId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
)

data class EmergencyContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
)

/** Número de un servicio oficial (policía, ambulancia, bomberos...). */
data class OfficialService(val name: String, val number: String)
