package cl.driverlink.app.di

import android.content.Context
import cl.driverlink.app.core.logging.LogcatAnalyticsTracker
import cl.driverlink.app.core.logging.LogcatCrashReporter
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.data.demo.DemoAlertRepository
import cl.driverlink.app.data.demo.DemoAuthRepository
import cl.driverlink.app.data.demo.DemoBackend
import cl.driverlink.app.data.demo.DemoBillingGateway
import cl.driverlink.app.data.demo.DemoChannelRepository
import cl.driverlink.app.data.demo.DemoCompanyRepository
import cl.driverlink.app.data.demo.DemoConfigRepository
import cl.driverlink.app.data.demo.DemoEmergencyContactRepository
import cl.driverlink.app.data.demo.DemoMessageRepository
import cl.driverlink.app.data.demo.DemoModerationRepository
import cl.driverlink.app.data.demo.DemoNotificationPreferencesRepository
import cl.driverlink.app.data.demo.DemoSosRepository
import cl.driverlink.app.data.demo.DemoSubscriptionRepository
import cl.driverlink.app.data.demo.DemoUserRepository
import cl.driverlink.app.data.demo.DemoVehicleRepository
import cl.driverlink.app.data.demo.DemoWorkSessionRepository
import cl.driverlink.app.domain.auth.ExternalAuthRegistry
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.services.notifications.LocalNotifier
import kotlinx.coroutines.CoroutineScope

/** Flavor demo: todo en memoria, sin Firebase ni credenciales. */
object FlavorModule {
    fun create(context: Context, clock: Clock, scope: CoroutineScope, notifier: LocalNotifier): DataModule {
        val backend = DemoBackend(context, clock, scope, notifier)
        val sos = DemoSosRepository(backend)
        return object : DataModule {
            override val authRepository = DemoAuthRepository(backend)
            override val userRepository = DemoUserRepository(backend)
            override val channelRepository = DemoChannelRepository(backend)
            override val messageRepository = DemoMessageRepository(backend)
            override val alertRepository = DemoAlertRepository(backend)
            override val sosRepository = sos
            override val sosOperatorRepository = sos
            override val emergencyContactRepository = DemoEmergencyContactRepository(backend)
            override val subscriptionRepository = DemoSubscriptionRepository(backend)
            override val billingGateway = DemoBillingGateway(backend)
            override val moderationRepository = DemoModerationRepository(backend)
            override val notificationPreferencesRepository = DemoNotificationPreferencesRepository(backend)
            override val configRepository = DemoConfigRepository(backend)
            override val companyRepository = DemoCompanyRepository(backend)
            override val vehicleRepository = DemoVehicleRepository(backend)
            override val workSessionRepository = DemoWorkSessionRepository(backend)
            override val analytics = LogcatAnalyticsTracker()
            override val crashReporter = LogcatCrashReporter()
            override val externalAuthRegistry = ExternalAuthRegistry()
            // Centro de Santiago para emuladores sin GPS.
            override val locationFallback: GeoPoint? = GeoPoint(-33.4489, -70.6693)
        }
    }
}
