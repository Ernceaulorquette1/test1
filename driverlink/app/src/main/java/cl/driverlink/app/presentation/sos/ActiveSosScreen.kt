package cl.driverlink.app.presentation.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.EmergencyContactRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.navigation.Routes
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.services.location.OperationalLocationService
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.components.formatClock
import cl.driverlink.app.ui.theme.DriverLinkThemeExt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActiveSosViewModel(
    savedStateHandle: SavedStateHandle,
    private val sosRepository: SosRepository,
    contactRepository: EmergencyContactRepository,
    configRepository: ConfigRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val sosId: String = checkNotNull(savedStateHandle[Routes.ARG_SOS_ID])
    private var resolvedLogged = false

    val sos: StateFlow<UiState<SosEvent>> = sosRepository.observeSos(sosId)
        .onEach { event ->
            if (event?.status == SosStatus.RESOLVED && !resolvedLogged) {
                resolvedLogged = true
                analytics.log(AnalyticsEvent.SOS_RESOLVED)
            }
        }
        .map { if (it == null) UiState.Empty else UiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val contacts: StateFlow<List<EmergencyContact>> = contactRepository.observeContacts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val config: StateFlow<AppConfig> = configRepository.config

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun cancel() {
        viewModelScope.launch {
            when (val result = sosRepository.cancelSos(sosId)) {
                is AppResult.Success -> analytics.log(AnalyticsEvent.SOS_CANCELLED)
                is AppResult.Failure -> _message.value = result.error.toUiText()
            }
        }
    }
}

/** Línea de tiempo: Buscando asistencia → Solicitud recibida → En atención → Finalizada. */
private val timeline = listOf(
    SosStatus.CREATED to R.string.sos_status_created,
    SosStatus.RECEIVED to R.string.sos_status_received,
    SosStatus.IN_PROGRESS to R.string.sos_status_in_progress,
    SosStatus.RESOLVED to R.string.sos_status_resolved,
)

@Composable
fun ActiveSosScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        ActiveSosViewModel(createSavedStateHandle(), it.sosRepository, it.emergencyContactRepository, it.configRepository, it.analytics)
    }
    val sosState by viewModel.sos.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.sos_active_title), onBack = onBack) }) { padding ->
        StateContent(sosState, emptyMessage = stringResource(R.string.sos_not_found), modifier = Modifier.padding(padding)) { sos ->
            LaunchedEffect(sos.status) {
                if (!sos.status.isActive) OperationalLocationService.stop(context)
            }
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (sos.status.isActive) DriverLinkThemeExt.colors.sos else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (sos.status.isActive) DriverLinkThemeExt.colors.onSos else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive }) {
                        Text(
                            stringResource(if (sos.status == SosStatus.CANCELLED) R.string.sos_status_cancelled else R.string.sos_sent),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(stringResource(R.string.sos_type_and_time, stringResource(sos.type.labelRes()), formatClock(sos.createdAt)))
                        if (sos.location == null) Text(stringResource(R.string.sos_no_location))
                    }
                }
                if (sos.status != SosStatus.CANCELLED) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentIndex = timeline.indexOfFirst { it.first == sos.status }
                        timeline.forEachIndexed { index, (_, label) ->
                            val reached = index <= currentIndex
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    if (reached) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = stringResource(if (reached) R.string.step_done else R.string.step_pending),
                                    tint = if (reached) DriverLinkThemeExt.colors.success else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    stringResource(label),
                                    style = if (index == currentIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                OfficialServicesBlock(config.officialServices)
                if (contacts.isNotEmpty()) {
                    Text(stringResource(R.string.emergency_contacts_title), style = MaterialTheme.typography.titleMedium)
                    contacts.forEach { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text(contact.relationship) },
                            trailingContent = {
                                IconButton(onClick = { dial(context, contact.phone) }) {
                                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.action_call_contact, contact.name))
                                }
                            },
                        )
                    }
                }
                message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                if (sos.status.isActive) {
                    OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.sos_cancel))
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.sos_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.sos_cancel_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text(stringResource(R.string.sos_cancel)) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}
