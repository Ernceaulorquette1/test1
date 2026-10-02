package cl.driverlink.app.di

import android.content.Context
import cl.driverlink.app.core.network.ConnectivityObserver
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.usecase.CreateAlertUseCase
import cl.driverlink.app.domain.usecase.CreateCompanyUseCase
import cl.driverlink.app.domain.usecase.CreateVehicleUseCase
import cl.driverlink.app.domain.usecase.JoinCompanyUseCase
import cl.driverlink.app.domain.usecase.LoginUseCase
import cl.driverlink.app.domain.usecase.RegisterUseCase
import cl.driverlink.app.domain.usecase.SendAudioMessageUseCase
import cl.driverlink.app.domain.usecase.SendMessageUseCase
import cl.driverlink.app.domain.usecase.StartSosUseCase
import cl.driverlink.app.domain.usecase.VoteAlertUseCase
import cl.driverlink.app.services.audio.AudioPlayer
import cl.driverlink.app.services.audio.AudioRecorder
import cl.driverlink.app.services.location.FusedLocationProvider
import cl.driverlink.app.services.location.LocationProvider
import cl.driverlink.app.services.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Inyección de dependencias manual (service locator con alcance de aplicación).
 * Se eligió en lugar de Hilt para mantener el build simple y sin procesadores de anotaciones;
 * el reemplazo por Hilt es directo porque todas las dependencias entran por constructor.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.System

    val notificationHelper = NotificationHelper(appContext)

    private val data: DataModule = FlavorModule.create(appContext, clock, applicationScope, notificationHelper)

    val authRepository get() = data.authRepository
    val userRepository get() = data.userRepository
    val channelRepository get() = data.channelRepository
    val messageRepository get() = data.messageRepository
    val alertRepository get() = data.alertRepository
    val sosRepository get() = data.sosRepository
    val sosOperatorRepository get() = data.sosOperatorRepository
    val emergencyContactRepository get() = data.emergencyContactRepository
    val subscriptionRepository get() = data.subscriptionRepository
    val billingGateway get() = data.billingGateway
    val moderationRepository get() = data.moderationRepository
    val notificationPreferencesRepository get() = data.notificationPreferencesRepository
    val configRepository get() = data.configRepository
    val companyRepository get() = data.companyRepository
    val vehicleRepository get() = data.vehicleRepository
    val workSessionRepository get() = data.workSessionRepository
    val analytics get() = data.analytics
    val crashReporter get() = data.crashReporter
    val externalAuthRegistry get() = data.externalAuthRegistry

    val locationProvider: LocationProvider = FusedLocationProvider(appContext, data.locationFallback)
    val audioRecorder by lazy { AudioRecorder(appContext) }
    val audioPlayer by lazy { AudioPlayer() }
    val connectivityObserver by lazy { ConnectivityObserver(appContext) }

    // Casos de uso
    val loginUseCase get() = LoginUseCase(authRepository)
    val registerUseCase get() = RegisterUseCase(authRepository)
    val createAlertUseCase get() = CreateAlertUseCase(alertRepository, configRepository, clock)
    val voteAlertUseCase get() = VoteAlertUseCase(alertRepository)
    val sendMessageUseCase get() = SendMessageUseCase(messageRepository)
    val sendAudioMessageUseCase get() = SendAudioMessageUseCase(messageRepository, configRepository)
    val startSosUseCase get() = StartSosUseCase(sosRepository, subscriptionRepository, configRepository, clock)
    val createCompanyUseCase get() = CreateCompanyUseCase(companyRepository)
    val joinCompanyUseCase get() = JoinCompanyUseCase(companyRepository)
    val createVehicleUseCase get() = CreateVehicleUseCase(vehicleRepository)
}
