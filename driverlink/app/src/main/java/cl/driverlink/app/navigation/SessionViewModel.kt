package cl.driverlink.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Estado global de sesión que decide qué grafo de navegación mostrar. */
class SessionViewModel(
    authRepository: AuthRepository,
    configRepository: ConfigRepository,
    analytics: AnalyticsTracker,
    crashReporter: CrashReporter,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .onEach { state ->
            val uid = (state as? AuthState.SignedIn)?.userId
            analytics.setUserId(uid)
            crashReporter.setUserId(uid)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unknown)

    val config: StateFlow<AppConfig> = configRepository.config
}
