package cl.driverlink.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.DefaultErrorMapper
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.ProfileUpdate
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import cl.driverlink.app.domain.repository.UserRepository
import cl.driverlink.app.domain.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileData(val user: User, val plan: SubscriptionStatus)

class ProfileViewModel(
    userRepository: UserRepository,
    subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<UiState<ProfileData>> = combine(
        userRepository.observeCurrentUser().filterNotNull(),
        subscriptionRepository.observeSubscription(),
    ) { user, sub ->
        val result: UiState<ProfileData> = UiState.Success(ProfileData(user, FeatureAccessPolicy.effectiveStatus(sub, clock.now())))
        result
    }
        .catch { emit(UiState.Error(DefaultErrorMapper.map(it).toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }
}

data class EditProfileUiState(
    val loaded: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val city: String = "",
    val commune: String = "",
    val platforms: Set<WorkPlatform> = emptySet(),
    val photoUrl: String? = null,
    val newPhotoUri: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: UiText? = null,
)

class EditProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = userRepository.observeCurrentUser().filterNotNull().first()
            _state.value = EditProfileUiState(
                loaded = true,
                firstName = user.firstName,
                lastName = user.lastName,
                phone = user.phone,
                city = user.city,
                commune = user.commune,
                platforms = user.platforms.toSet(),
                photoUrl = user.photoUrl,
            )
        }
    }

    fun update(transform: (EditProfileUiState) -> EditProfileUiState) = _state.update { transform(it).copy(error = null) }

    fun togglePlatform(platform: WorkPlatform) = _state.update {
        it.copy(platforms = if (platform in it.platforms) it.platforms - platform else it.platforms + platform)
    }

    fun save() {
        val s = _state.value
        val valid = Validators.isValidName(s.firstName) && Validators.isValidName(s.lastName) &&
            Validators.isValidPhone(s.phone) && s.city.isNotBlank() && s.commune.isNotBlank() && s.platforms.isNotEmpty()
        if (!valid) {
            _state.update { it.copy(error = UiText.Res(R.string.error_form_incomplete)) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = userRepository.updateProfile(
                ProfileUpdate(
                    firstName = s.firstName.trim(),
                    lastName = s.lastName.trim(),
                    phone = Validators.normalizeChileanPhone(s.phone) ?: s.phone,
                    city = s.city.trim(),
                    commune = s.commune.trim(),
                    platforms = s.platforms,
                    newPhotoUri = s.newPhotoUri,
                )
            )
            _state.update {
                when (result) {
                    is AppResult.Success -> it.copy(saving = false, saved = true)
                    is AppResult.Failure -> it.copy(saving = false, error = result.error.toUiText())
                }
            }
        }
    }
}
