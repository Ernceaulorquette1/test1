package cl.driverlink.app.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.repository.UserRepository
import cl.driverlink.app.domain.usecase.CreateAlertUseCase
import cl.driverlink.app.services.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateAlertUiState(
    val category: AlertCategory? = null,
    val description: String = "",
    val location: GeoPoint? = null,
    val locating: Boolean = false,
    val confirming: Boolean = false,
    val publishing: Boolean = false,
    val createdId: String? = null,
    val error: UiText? = null,
) {
    val canContinue: Boolean get() = category != null && location != null && !publishing
}

/** Flujo: categoría → descripción → ubicación → confirmar → publicar. */
class CreateAlertViewModel(
    private val createAlert: CreateAlertUseCase,
    private val userRepository: UserRepository,
    private val locationProvider: LocationProvider,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAlertUiState())
    val state: StateFlow<CreateAlertUiState> = _state.asStateFlow()

    fun selectCategory(category: AlertCategory) = _state.update { it.copy(category = category, error = null) }

    fun onDescription(value: String) =
        _state.update { it.copy(description = value.take(CreateAlertUseCase.MAX_DESCRIPTION)) }

    fun locate(permissionGranted: Boolean) {
        if (!permissionGranted) {
            _state.update { it.copy(error = UiText.Res(R.string.error_location_needed_alert)) }
            return
        }
        _state.update { it.copy(locating = true, error = null) }
        viewModelScope.launch {
            when (val result = locationProvider.currentLocation()) {
                is AppResult.Success -> _state.update { it.copy(locating = false, location = result.data) }
                is AppResult.Failure -> _state.update { it.copy(locating = false, error = result.error.toUiText()) }
            }
        }
    }

    fun requestConfirmation() {
        if (_state.value.canContinue) _state.update { it.copy(confirming = true) }
    }

    fun dismissConfirmation() = _state.update { it.copy(confirming = false) }

    fun publish() {
        val current = _state.value
        val category = current.category ?: return
        val location = current.location ?: return
        _state.update { it.copy(confirming = false, publishing = true, error = null) }
        viewModelScope.launch {
            val user = userRepository.observeCurrentUser().first()
            val draft = AlertDraft(
                category = category,
                description = current.description,
                location = location,
                city = user?.city.orEmpty(),
                commune = user?.commune.orEmpty(),
            )
            when (val result = createAlert(draft)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.ALERT_CREATED, mapOf("category" to category.name))
                    _state.update { it.copy(publishing = false, createdId = result.data) }
                }
                is AppResult.Failure -> _state.update { it.copy(publishing = false, error = result.error.toUiText()) }
            }
        }
    }
}
