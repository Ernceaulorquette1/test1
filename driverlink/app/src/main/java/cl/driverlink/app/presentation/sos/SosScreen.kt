package cl.driverlink.app.presentation.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.presentation.subscription.PremiumGate
import cl.driverlink.app.services.location.OperationalLocationService
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.SosHoldButton
import cl.driverlink.app.ui.theme.DriverLinkThemeExt

@Composable
fun SosScreen(onBack: () -> Unit, onSeePlans: () -> Unit, onSosActive: (String) -> Unit) {
    val viewModel = appViewModel {
        SosViewModel(it.startSosUseCase, it.sosRepository, it.locationProvider, it.configRepository, it.analytics)
    }
    val config by viewModel.config.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.sos_title), onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            PremiumGate(
                feature = PremiumFeature.SOS,
                lockedTitle = stringResource(R.string.sos_locked_title),
                onSeePlans = onSeePlans,
                // Los servicios oficiales siempre están disponibles, incluso sin Premium.
                lockedExtra = { OfficialServicesBlock(config.officialServices) },
            ) {
                SosContent(viewModel, onSosActive)
            }
        }
    }
}

@Composable
private fun SosContent(viewModel: SosViewModel, onSosActive: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val active by viewModel.activeSos.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requestLocation = rememberPermissionRequest(
        AppPermissions.LOCATION,
        stringResource(R.string.permission_location_sos_rationale),
    ) { granted -> viewModel.confirm(granted) }

    LaunchedEffect(state.createdId) {
        state.createdId?.let { id ->
            // Ubicación compartida solo mientras el SOS siga activo (el servicio se detiene solo).
            if (AppPermissions.anyGranted(context, AppPermissions.LOCATION)) {
                OperationalLocationService.startForSos(context, id)
            }
            viewModel.navigated()
            onSosActive(id)
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        active?.let { sos ->
            Card(
                onClick = { onSosActive(sos.id) },
                colors = CardDefaults.cardColors(containerColor = DriverLinkThemeExt.colors.sos),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sos_active_banner)) },
                    supportingContent = { Text(stringResource(R.string.sos_active_banner_body)) },
                )
            }
        }
        Text(stringResource(R.string.sos_intro), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (state.step == SosStep.Sending) {
            CircularProgressIndicator()
            Text(stringResource(R.string.sos_sending))
        } else {
            SosHoldButton(holdMillis = config.sosHoldMillis, onActivated = viewModel::onHoldCompleted)
        }
        state.error?.let {
            Text(stringResource(R.string.sos_not_sent), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            Text(it.asString(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        OfficialServicesBlock(config.officialServices)
    }

    when (val step = state.step) {
        SosStep.ChoosingType -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.sos_choose_type)) },
            text = {
                Column {
                    SosType.entries.forEach { type ->
                        TextButton(
                            onClick = { viewModel.chooseType(type) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text(stringResource(type.labelRes()), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is SosStep.Confirming -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.sos_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sos_confirm_body, stringResource(step.type.labelRes())))
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::onNotes,
                        label = { Text(stringResource(R.string.sos_notes_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = requestLocation) {
                    Text(stringResource(R.string.sos_send), color = DriverLinkThemeExt.colors.sos)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        else -> Unit
    }
}
