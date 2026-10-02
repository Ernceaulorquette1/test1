package cl.driverlink.app.presentation.company

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.DefaultErrorMapper
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toListUiState
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.CompanyDraft
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.DutyStatus
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.Membership
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.model.WorkSession
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.domain.policy.RolePolicy
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.VehicleRepository
import cl.driverlink.app.domain.repository.WorkSessionRepository
import cl.driverlink.app.domain.usecase.CompanyField
import cl.driverlink.app.domain.usecase.CompanyValidator
import cl.driverlink.app.domain.usecase.CreateCompanyUseCase
import cl.driverlink.app.domain.usecase.CreateVehicleUseCase
import cl.driverlink.app.domain.usecase.JoinCompanyUseCase
import cl.driverlink.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class CompanyEntryViewModel(companyRepository: CompanyRepository, configRepository: ConfigRepository) : ViewModel() {
    val enabled: Boolean = configRepository.config.value.companyFeaturesEnabled
    val memberships: StateFlow<UiState<List<Membership>>> = companyRepository.observeMyMemberships()
        .map { list -> list.filter { it.member.status != MemberStatus.REMOVED }.toListUiState() }
        .catch { emit(UiState.Error(DefaultErrorMapper.map(it).toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

data class CreateCompanyUiState(
    val draft: CompanyDraft = CompanyDraft("", "", "", "", "", ""),
    val errors: Set<CompanyField> = emptySet(),
    val saving: Boolean = false,
    val createdId: String? = null,
    val error: UiText? = null,
)

class CreateCompanyViewModel(
    private val createCompany: CreateCompanyUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateCompanyUiState())
    val state: StateFlow<CreateCompanyUiState> = _state.asStateFlow()

    fun update(transform: (CompanyDraft) -> CompanyDraft) = _state.update { it.copy(draft = transform(it.draft), error = null) }

    fun submit() {
        val draft = _state.value.draft
        val errors = CompanyValidator.validate(draft)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, error = UiText.Res(R.string.error_form_incomplete)) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptySet()) }
        viewModelScope.launch {
            when (val result = createCompany(draft)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.COMPANY_CREATED)
                    _state.update { it.copy(saving = false, createdId = result.data) }
                }
                is AppResult.Failure -> _state.update { it.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }
}

data class JoinCompanyUiState(val code: String = "", val sending: Boolean = false, val sent: Boolean = false, val error: UiText? = null)

class JoinCompanyViewModel(
    private val joinCompany: JoinCompanyUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinCompanyUiState())
    val state: StateFlow<JoinCompanyUiState> = _state.asStateFlow()

    fun onCode(value: String) = _state.update { it.copy(code = value.uppercase().take(11), error = null) }

    fun submit() {
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val result = joinCompany(_state.value.code)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.COMPANY_JOIN_REQUESTED)
                    _state.update { it.copy(sending = false, sent = true) }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        sending = false,
                        error = if (result.error is cl.driverlink.app.core.result.AppError.Validation) UiText.Res(R.string.join_code_invalid)
                        else result.error.toUiText(),
                    )
                }
            }
        }
    }
}

data class CompanyHomeData(
    val membership: Membership,
    val textChannels: List<Channel>,
    val radioChannels: List<Channel>,
    val session: WorkSession?,
) {
    fun can(permission: CompanyPermission) = RolePolicy.has(membership.member, permission)
}

class CompanyHomeViewModel(
    savedStateHandle: SavedStateHandle,
    private val companyRepository: CompanyRepository,
    channelRepository: ChannelRepository,
    private val workSessionRepository: WorkSessionRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    val companyId: String = checkNotNull(savedStateHandle[Routes.ARG_COMPANY_ID])

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    /** Jornada recién iniciada: la UI arranca el servicio de ubicación operacional. */
    private val _startedSession = MutableStateFlow<String?>(null)
    val startedSession: StateFlow<String?> = _startedSession.asStateFlow()

    val state: StateFlow<UiState<CompanyHomeData>> = combine(
        companyRepository.observeMyMemberships().map { list -> list.firstOrNull { it.company.id == companyId } },
        channelRepository.observeCompanyChannels(companyId, ChannelMode.TEXT),
        channelRepository.observeCompanyChannels(companyId, ChannelMode.RADIO),
        workSessionRepository.observeActiveSession(companyId),
    ) { membership, text, radio, session ->
        // Sin membresía ACTIVE no se muestra nada de la empresa (aunque se navegue directo).
        val result: UiState<CompanyHomeData> = if (membership == null || membership.member.status != MemberStatus.ACTIVE) {
            UiState.Error(UiText.Res(R.string.company_no_access))
        } else {
            UiState.Success(CompanyHomeData(membership, text, radio, session))
        }
        result
    }
        .catch { emit(UiState.Error(UiText.Res(R.string.company_no_access))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun startSession() {
        viewModelScope.launch {
            when (val result = workSessionRepository.startSession(companyId, vehicleId = null)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.WORK_SESSION_STARTED)
                    _startedSession.value = result.data
                }
                is AppResult.Failure -> _message.value = result.error.toUiText()
            }
        }
    }

    fun sessionTrackingStarted() { _startedSession.value = null }

    fun setStatus(session: WorkSession, status: DutyStatus) {
        viewModelScope.launch {
            val result = workSessionRepository.updateStatus(companyId, session.id, status)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }

    fun endSession(session: WorkSession) {
        viewModelScope.launch {
            val result = workSessionRepository.endSession(companyId, session.id)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }

    fun regenerateCode() {
        viewModelScope.launch {
            val result = companyRepository.regenerateInviteCode(companyId)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }
}

class CompanyMembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val companyRepository: CompanyRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val companyId: String = checkNotNull(savedStateHandle[Routes.ARG_COMPANY_ID])
    private val myId = authRepository.currentUserId()

    data class MembersData(val members: List<CompanyMember>, val canApprove: Boolean, val canView: Boolean)

    val state: StateFlow<UiState<MembersData>> = combine(
        companyRepository.observeMembers(companyId),
        companyRepository.observeMyMemberships(),
    ) { members, memberships ->
        val me = memberships.firstOrNull { it.company.id == companyId }?.member
        val result: UiState<MembersData> = if (!RolePolicy.has(me, CompanyPermission.VIEW_MEMBERS)) {
            UiState.Error(UiText.Res(R.string.company_no_permission))
        } else {
            UiState.Success(
                MembersData(
                    members = members.filter { it.status != MemberStatus.REMOVED }.sortedBy { it.status.ordinal },
                    canApprove = RolePolicy.has(me, CompanyPermission.APPROVE_MEMBERS),
                    canView = true,
                )
            )
        }
        result
    }
        .catch { emit(UiState.Error(UiText.Res(R.string.company_no_permission))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun review(member: CompanyMember, approve: Boolean) {
        if (member.userId == myId) return
        viewModelScope.launch {
            val result = companyRepository.reviewJoinRequest(companyId, member.userId, approve)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }
}

class VehiclesViewModel(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    companyRepository: CompanyRepository,
    private val createVehicle: CreateVehicleUseCase,
) : ViewModel() {
    private val companyId: String = checkNotNull(savedStateHandle[Routes.ARG_COMPANY_ID])

    data class VehiclesData(
        val vehicles: List<Vehicle>,
        val drivers: List<CompanyMember>,
        val canManage: Boolean,
        val canAssign: Boolean,
    )

    val state: StateFlow<UiState<VehiclesData>> = combine(
        vehicleRepository.observeVehicles(companyId),
        companyRepository.observeMembers(companyId),
        companyRepository.observeMyMemberships(),
    ) { vehicles, members, memberships ->
        val me = memberships.firstOrNull { it.company.id == companyId }?.member
        val result: UiState<VehiclesData> = if (me == null || me.status != MemberStatus.ACTIVE) {
            UiState.Error(UiText.Res(R.string.company_no_access))
        } else {
            UiState.Success(
                VehiclesData(
                    vehicles = vehicles.sortedBy { it.plate },
                    drivers = members.filter { it.status == MemberStatus.ACTIVE },
                    canManage = RolePolicy.has(me, CompanyPermission.MANAGE_VEHICLES),
                    canAssign = RolePolicy.has(me, CompanyPermission.ASSIGN_VEHICLES),
                )
            )
        }
        result
    }
        .catch { emit(UiState.Error(UiText.Res(R.string.company_no_access))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun create(draft: VehicleDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = createVehicle(companyId, draft, Calendar.getInstance().get(Calendar.YEAR))) {
                is AppResult.Success -> { _message.value = null; onDone() }
                is AppResult.Failure -> _message.value =
                    if (result.error is cl.driverlink.app.core.result.AppError.Validation) UiText.Res(R.string.vehicle_invalid)
                    else result.error.toUiText()
            }
        }
    }

    fun assign(vehicle: Vehicle, driver: CompanyMember?) {
        viewModelScope.launch {
            val result = vehicleRepository.assignVehicle(companyId, vehicle.id, driver)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }
}
