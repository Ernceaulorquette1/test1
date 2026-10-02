package cl.driverlink.app.presentation.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.domain.usecase.StartSosUseCase
import cl.driverlink.app.services.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SosStep {
    data object Idle : SosStep
    data object ChoosingType : SosStep
    data class Confirming(val type: SosType) : SosStep
    data object Sending : SosStep
}

data class SosUiState(
    val step: SosStep = SosStep.Idle,
    val notes: String = "",
    val createdId: String? = null,
    val error: UiText? = null,
)

class SosViewModel(
    private val startSos: StartSosUseCase,
    sosRepository: SosRepository,
    private val locationProvider: LocationProvider,
    configRepository: ConfigRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(SosUiState())
    val state: StateFlow<SosUiState> = _state.asStateFlow()
    val config: StateFlow<AppConfig> = configRepository.config

    val activeSos: StateFlow<SosEvent?> = sosRepository.observeMyActiveSos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Se completó la pulsación larga de ~3 s. */
    fun onHoldCompleted() = _state.update { it.copy(step = SosStep.ChoosingType, error = null) }

    fun chooseType(type: SosType) = _state.update { it.copy(step = SosStep.Confirming(type)) }

    fun onNotes(value: String) = _state.update { it.copy(notes = value.take(500)) }

    fun dismiss() = _state.update { it.copy(step = SosStep.Idle) }

    /** Confirmado por el usuario: obtiene ubicación (si hay permiso) y crea el evento en servidor. */
    fun confirm(locationGranted: Boolean) {
        val step = _state.value.step as? SosStep.Confirming ?: return
        _state.update { it.copy(step = SosStep.Sending, error = null) }
        viewModelScope.launch {
            val location = if (locationGranted) {
                (locationProvider.currentLocation() as? AppResult.Success)?.data
            } else null
            when (val result = startSos(step.type, location, _state.value.notes)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.SOS_STARTED, mapOf("type" to step.type.name))
                    _state.update { it.copy(step = SosStep.Idle, createdId = result.data) }
                }
                is AppResult.Failure -> _state.update {
                    // Acción crítica: se informa claramente que NO se envió.
                    it.copy(step = SosStep.Idle, error = result.error.toUiText())
                }
            }
        }
    }

    fun navigated() = _state.update { it.copy(createdId = null) }
}

fun SosType.labelRes(): Int = when (this) {
    SosType.ROBBERY -> R.string.sos_type_robbery
    SosType.ACCIDENT -> R.string.sos_type_accident
    SosType.PASSENGER_ISSUE -> R.string.sos_type_passenger
    SosType.MECHANICAL -> R.string.sos_type_mechanical
    SosType.MEDICAL -> R.string.sos_type_medical
    SosType.OTHER -> R.string.sos_type_other
}
