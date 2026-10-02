package cl.driverlink.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.core.result.DefaultErrorMapper
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.VerificationStatus
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeData(
    val user: User,
    val activeAlerts: Int,
    val recentAlerts: List<Alert>,
    val channels: List<Channel>,
) {
    val needsVerification: Boolean
        get() = user.verificationStatus == VerificationStatus.UNVERIFIED || user.verificationStatus == VerificationStatus.REJECTED
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    userRepository: UserRepository,
    alertRepository: AlertRepository,
    channelRepository: ChannelRepository,
    private val clock: Clock,
) : ViewModel() {

    private val user = userRepository.observeCurrentUser().filterNotNull()

    // Solo se escuchan las alertas de la ciudad del usuario (no todo Chile).
    private val alerts = user.map { it.city }.distinctUntilChanged()
        .flatMapLatest { city -> alertRepository.observeActiveAlerts(city) }

    val state: StateFlow<UiState<HomeData>> = combine(
        user,
        alerts,
        channelRepository.observeCommunityChannels(ChannelMode.TEXT),
    ) { user, alerts, channels ->
        val now = clock.now()
        val active = alerts.filter { it.isActiveAt(now) }.sortedByDescending { it.createdAt }
        val result: UiState<HomeData> = UiState.Success(
            HomeData(
                user = user,
                activeAlerts = active.size,
                recentAlerts = active.take(5),
                channels = channels.filter { it.city.equals(user.city, ignoreCase = true) || it.city.isBlank() }.take(4)
                    .ifEmpty { channels.take(4) },
            )
        )
        result
    }
        .catch { emit(UiState.Error(DefaultErrorMapper.map(it).toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
