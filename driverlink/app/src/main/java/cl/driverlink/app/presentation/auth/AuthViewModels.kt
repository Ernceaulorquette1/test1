package cl.driverlink.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.usecase.LoginUseCase
import cl.driverlink.app.domain.usecase.RegisterUseCase
import cl.driverlink.app.domain.usecase.RegistrationField
import cl.driverlink.app.domain.usecase.RegistrationValidator
import cl.driverlink.app.domain.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
)

class LoginViewModel(
    private val login: LoginUseCase,
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null, info = null) }
        viewModelScope.launch {
            when (val result = login(_state.value.email, _state.value.password)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.LOGIN_COMPLETED)
                    _state.update { it.copy(loading = false) }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = loginError(result.error))
                }
            }
        }
    }

    fun resetPassword() {
        val email = _state.value.email
        if (!Validators.isValidEmail(email)) {
            _state.update { it.copy(error = UiText.Res(R.string.error_email_invalid)) }
            return
        }
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordReset(email.trim())) {
                is AppResult.Success -> _state.update { it.copy(info = UiText.Res(R.string.login_reset_sent)) }
                is AppResult.Failure -> _state.update { it.copy(error = result.error.toUiText()) }
            }
        }
    }

    private fun loginError(error: AppError): UiText = when (error) {
        is AppError.Validation -> UiText.Res(
            if (error.field == "EMAIL") R.string.error_email_invalid else R.string.error_password_required
        )
        else -> error.toUiText()
    }
}

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val city: String = "",
    val commune: String = "",
    val platforms: Set<WorkPlatform> = emptySet(),
    val photoUri: String? = null,
    val acceptedTerms: Boolean = false,
    val fieldErrors: Set<RegistrationField> = emptySet(),
    val loading: Boolean = false,
    val error: UiText? = null,
)

class RegisterViewModel(
    private val register: RegisterUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun update(transform: (RegisterUiState) -> RegisterUiState) =
        _state.update { transform(it).copy(error = null) }

    fun togglePlatform(platform: WorkPlatform) = _state.update {
        val platforms = if (platform in it.platforms) it.platforms - platform else it.platforms + platform
        it.copy(platforms = platforms)
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val data = current.toRegistrationData()
        val errors = RegistrationValidator.validate(data)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, error = UiText.Res(R.string.error_form_incomplete)) }
            return
        }
        if (!current.acceptedTerms) {
            _state.update { it.copy(fieldErrors = emptySet(), error = UiText.Res(R.string.register_accept_terms_required)) }
            return
        }
        _state.update { it.copy(loading = true, fieldErrors = emptySet()) }
        viewModelScope.launch {
            when (val result = register(data)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.REGISTRATION_COMPLETED, mapOf("platforms" to data.platforms.size.toString()))
                    _state.update { it.copy(loading = false) }
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }

    private fun RegisterUiState.toRegistrationData() = RegistrationData(
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        password = password,
        city = city,
        commune = commune,
        platforms = platforms,
        photoUri = photoUri,
    )
}
