package cl.driverlink.app.domain.model

enum class AlertCategory {
    ACCIDENT, TRAFFIC, ROAD_CLOSED, DANGER, SECURITY, ROAD_PROBLEM, EMERGENCY, OTHER
}

enum class AlertStatus { ACTIVE, RESOLVED, EXPIRED, HIDDEN }

/** Acción de un conductor sobre una alerta. Una sola por usuario y alerta. */
enum class AlertVote { CONFIRM, ENDED, INCORRECT }

data class Alert(
    val id: String,
    val creatorId: String,
    val creatorName: String,
    val category: AlertCategory,
    val description: String,
    val location: GeoPoint,
    val city: String,
    val commune: String,
    val status: AlertStatus,
    val confirmationsCount: Int,
    val endedCount: Int,
    val incorrectCount: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val updatedAt: Long,
) {
    fun isActiveAt(now: Long): Boolean = status == AlertStatus.ACTIVE && expiresAt > now
}

data class AlertDraft(
    val category: AlertCategory,
    val description: String,
    val location: GeoPoint,
    val city: String,
    val commune: String,
)
