package cl.driverlink.app.domain.usecase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.policy.AlertExpirationPolicy
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.ConfigRepository

class CreateAlertUseCase(
    private val alertRepository: AlertRepository,
    private val configRepository: ConfigRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: AlertDraft): AppResult<String> {
        val description = draft.description.trim()
        if (description.length > MAX_DESCRIPTION) return AppResult.Failure(AppError.Validation("DESCRIPTION"))
        if (configRepository.config.value.maintenanceMode) return AppResult.Failure(AppError.Maintenance)
        val policy = AlertExpirationPolicy(configRepository.config.value.alertExpirationMinutes)
        val expiresAt = policy.expiresAt(draft.category, clock.now())
        return alertRepository.createAlert(draft.copy(description = description), expiresAt)
    }

    companion object {
        const val MAX_DESCRIPTION = 280
    }
}

class VoteAlertUseCase(private val alertRepository: AlertRepository) {
    suspend operator fun invoke(alertId: String, vote: AlertVote): AppResult<Unit> {
        val previous = alertRepository.myVote(alertId)
        if (previous is AppResult.Success && previous.data != null) {
            return AppResult.Failure(AppError.AlreadyVoted)
        }
        return alertRepository.vote(alertId, vote)
    }
}
