package cl.driverlink.app.di

import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.domain.auth.ExternalAuthRegistry
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.BillingGateway
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.EmergencyContactRepository
import cl.driverlink.app.domain.repository.MessageRepository
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.domain.repository.NotificationPreferencesRepository
import cl.driverlink.app.domain.repository.SosOperatorRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import cl.driverlink.app.domain.repository.UserRepository
import cl.driverlink.app.domain.repository.VehicleRepository
import cl.driverlink.app.domain.repository.WorkSessionRepository

/**
 * Contrato que cada flavor implementa en su `FlavorModule`:
 *  - `demo`: repositorios en memoria con datos de prueba (src/demo).
 *  - `prod`: repositorios Firebase (src/prod).
 * La capa de presentación solo conoce estas interfaces.
 */
interface DataModule {
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val channelRepository: ChannelRepository
    val messageRepository: MessageRepository
    val alertRepository: AlertRepository
    val sosRepository: SosRepository
    val sosOperatorRepository: SosOperatorRepository
    val emergencyContactRepository: EmergencyContactRepository
    val subscriptionRepository: SubscriptionRepository
    val billingGateway: BillingGateway
    val moderationRepository: ModerationRepository
    val notificationPreferencesRepository: NotificationPreferencesRepository
    val configRepository: ConfigRepository
    val companyRepository: CompanyRepository
    val vehicleRepository: VehicleRepository
    val workSessionRepository: WorkSessionRepository
    val analytics: AnalyticsTracker
    val crashReporter: CrashReporter
    val externalAuthRegistry: ExternalAuthRegistry
    /** Ubicación de respaldo solo para demo en emuladores; null en producción. */
    val locationFallback: GeoPoint?
}
