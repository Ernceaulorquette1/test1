package cl.driverlink.app.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.UserRepository
import cl.driverlink.app.domain.usecase.VoteAlertUseCase
import cl.driverlink.app.services.location.LocationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val alerts: List<Alert> = emptyList(),
    val center: GeoPoint = DEFAULT_CENTER,
    val myLocationEnabled: Boolean = false,
    val selected: Alert? = null,
    val loading: Boolean = true,
    val message: UiText? = null,
) {
    companion object {
        /** Centro de Santiago, usado solo hasta conocer la ubicación del usuario. */
        val DEFAULT_CENTER = GeoPoint(-33.4489, -70.6693)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    userRepository: UserRepository,
    alertRepository: AlertRepository,
    private val voteAlert: VoteAlertUseCase,
    private val locationProvider: LocationProvider,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(MapUiState())

    private val alerts = userRepository.observeCurrentUser()
        .map { it?.city }
        .distinctUntilChanged()
        .flatMapLatest { city -> alertRepository.observeActiveAlerts(city) }

    val state: StateFlow<MapUiState> = combine(local, alerts) { l, list ->
        val now = clock.now()
        val active = list.filter { it.isActiveAt(now) }
        l.copy(alerts = active, loading = false, selected = l.selected?.let { sel -> active.firstOrNull { it.id == sel.id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun onLocationPermission(granted: Boolean) {
        local.update { it.copy(myLocationEnabled = granted && locationProvider.hasPermission()) }
        if (!granted) return
        viewModelScope.launch {
            val result = locationProvider.currentLocation()
            if (result is AppResult.Success) local.update { it.copy(center = result.data) }
        }
    }

    fun select(alert: Alert?) = local.update { it.copy(selected = alert) }

    fun vote(alert: Alert, vote: AlertVote) {
        viewModelScope.launch {
            when (val result = voteAlert(alert.id, vote)) {
                is AppResult.Success -> {
                    if (vote == AlertVote.CONFIRM) analytics.log(AnalyticsEvent.ALERT_CONFIRMED)
                    local.update { it.copy(message = UiText.Res(R.string.alert_vote_thanks)) }
                }
                is AppResult.Failure -> local.update { it.copy(message = result.error.toUiText()) }
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }
}
