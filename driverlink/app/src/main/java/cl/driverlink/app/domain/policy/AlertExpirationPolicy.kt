package cl.driverlink.app.domain.policy

import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AppConfig

/** Calcula el vencimiento de una alerta según la configuración remota. */
class AlertExpirationPolicy(private val minutesByCategory: Map<AlertCategory, Int>) {

    fun expiresAt(category: AlertCategory, createdAt: Long): Long {
        val minutes = minutesByCategory[category]
            ?: AppConfig.DEFAULT_ALERT_EXPIRATION.getValue(category)
        return createdAt + minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * 60_000L
    }

    companion object {
        const val MIN_MINUTES = 5
        /** Límite superior también exigido por firestore.rules (24 h). */
        const val MAX_MINUTES = 24 * 60
    }
}
