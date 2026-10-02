package cl.driverlink.app.domain.usecase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first

/**
 * Inicia un SOS. La verificación Premium aquí evita viajes inútiles al servidor,
 * pero la Cloud Function `createSosEvent` vuelve a validarla.
 */
class StartSosUseCase(
    private val sosRepository: SosRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val configRepository: ConfigRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(type: SosType, location: GeoPoint?, notes: String): AppResult<String> {
        if (!configRepository.config.value.sosEnabled) return AppResult.Failure(AppError.FeatureDisabled)
        val subscription = subscriptionRepository.observeSubscription().first()
        if (!FeatureAccessPolicy.hasAccess(subscription, PremiumFeature.SOS, clock.now())) {
            return AppResult.Failure(AppError.PremiumRequired)
        }
        val active = sosRepository.observeMyActiveSos().first()
        if (active != null) return AppResult.Success(active.id)
        return sosRepository.createSos(type, location, notes.trim().take(500))
    }
}
