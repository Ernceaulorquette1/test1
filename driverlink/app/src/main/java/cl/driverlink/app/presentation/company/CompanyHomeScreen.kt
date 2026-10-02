package cl.driverlink.app.presentation.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.DutyStatus
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.services.location.OperationalLocationService
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.components.formatClock

fun DutyStatus.labelRes(): Int = when (this) {
    DutyStatus.OFF_DUTY -> R.string.duty_off
    DutyStatus.ON_DUTY -> R.string.duty_on
    DutyStatus.BREAK -> R.string.duty_break
    DutyStatus.EMERGENCY -> R.string.duty_emergency
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanyHomeScreen(
    onBack: () -> Unit,
    onMembers: (String) -> Unit,
    onVehicles: (String) -> Unit,
    onChannel: (String, String) -> Unit,
    onRadio: (String, String) -> Unit,
) {
    val viewModel = appViewModel {
        CompanyHomeViewModel(createSavedStateHandle(), it.companyRepository, it.channelRepository, it.workSessionRepository, it.analytics)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val startedSession by viewModel.startedSession.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val companyId = viewModel.companyId

    val requestLocation = rememberPermissionRequest(
        AppPermissions.LOCATION,
        stringResource(R.string.permission_location_work_rationale),
    ) { granted -> if (granted) viewModel.startSession() }

    LaunchedEffect(startedSession) {
        startedSession?.let { sessionId ->
            OperationalLocationService.startForWorkSession(context, companyId, sessionId)
            viewModel.sessionTrackingStarted()
        }
    }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.space_company), onBack = onBack) }) { padding ->
        StateContent(state, emptyMessage = "", modifier = Modifier.padding(padding)) { data ->
            val company = data.membership.company
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(company.tradeName, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(data.membership.member.role.labelRes()))

                if (data.can(CompanyPermission.APPROVE_MEMBERS)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.company_invite_code), style = MaterialTheme.typography.labelLarge)
                            Text(company.inviteCode ?: "—", style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.company_invite_hint), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::regenerateCode) { Text(stringResource(R.string.company_regenerate_code)) }
                        }
                    }
                }

                if (data.can(CompanyPermission.START_WORK_SESSION)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.work_session_title), style = MaterialTheme.typography.titleMedium)
                            val session = data.session
                            if (session == null) {
                                Text(stringResource(R.string.work_session_off))
                                Text(stringResource(R.string.work_session_tracking_note), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = requestLocation, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(stringResource(R.string.work_session_start))
                                }
                            } else {
                                Text(stringResource(R.string.work_session_since, formatClock(session.startedAt), stringResource(session.status.labelRes())))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(DutyStatus.ON_DUTY, DutyStatus.BREAK, DutyStatus.EMERGENCY).forEach { status ->
                                        FilterChip(
                                            selected = session.status == status,
                                            onClick = { viewModel.setStatus(session, status) },
                                            label = { Text(stringResource(status.labelRes())) },
                                        )
                                    }
                                }
                                OutlinedButton(onClick = {
                                    viewModel.endSession(session)
                                    OperationalLocationService.stop(context)
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(stringResource(R.string.work_session_end))
                                }
                            }
                        }
                    }
                }

                Text(stringResource(R.string.company_channels), style = MaterialTheme.typography.titleMedium)
                data.textChannels.forEach { channel ->
                    Card(onClick = { onChannel(channel.id, companyId) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(channel.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                        )
                    }
                }
                data.radioChannels.forEach { channel ->
                    Card(onClick = { onRadio(channel.id, companyId) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(channel.name) },
                            leadingContent = { Icon(Icons.Filled.SettingsVoice, contentDescription = null) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (data.can(CompanyPermission.VIEW_MEMBERS)) {
                        OutlinedButton(onClick = { onMembers(companyId) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Icon(Icons.Filled.Group, contentDescription = null)
                            Text(" " + stringResource(R.string.company_members))
                        }
                    }
                    OutlinedButton(onClick = { onVehicles(companyId) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                        Text(" " + stringResource(R.string.company_vehicles))
                    }
                }
                message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
