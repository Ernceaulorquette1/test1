package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertVote
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    /** Alertas activas y no expiradas, filtradas por ciudad para limitar lecturas. */
    fun observeActiveAlerts(city: String?): Flow<List<Alert>>
    fun observeAlert(alertId: String): Flow<Alert?>
    suspend fun createAlert(draft: AlertDraft, expiresAt: Long): AppResult<String>
    /** Registra la acción del usuario. Un voto por usuario: el segundo devuelve AlreadyVoted. */
    suspend fun vote(alertId: String, vote: AlertVote): AppResult<Unit>
    suspend fun myVote(alertId: String): AppResult<AlertVote?>
}
