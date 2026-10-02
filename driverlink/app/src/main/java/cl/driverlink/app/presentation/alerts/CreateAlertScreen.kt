package cl.driverlink.app.presentation.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.PrimaryButton
import cl.driverlink.app.ui.theme.style

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAlertScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val viewModel = appViewModel { CreateAlertViewModel(it.createAlertUseCase, it.userRepository, it.locationProvider, it.analytics) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestLocation = rememberPermissionRequest(
        AppPermissions.LOCATION,
        stringResource(R.string.permission_location_alert_rationale),
        viewModel::locate,
    )

    LaunchedEffect(Unit) {
        if (AppPermissions.anyGranted(context, AppPermissions.LOCATION)) viewModel.locate(true)
    }
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.create_alert_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.create_alert_step_category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertCategory.entries.forEach { category ->
                    CategoryTile(category, selected = state.category == category, onClick = { viewModel.selectCategory(category) })
                }
            }
            Text(stringResource(R.string.create_alert_step_description), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                placeholder = { Text(stringResource(R.string.create_alert_description_hint)) },
                supportingText = { Text("${state.description.length}/280") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.create_alert_step_location), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    state.locating -> stringResource(R.string.create_alert_locating)
                    state.location != null -> stringResource(R.string.create_alert_location_ready)
                    else -> stringResource(R.string.create_alert_location_missing)
                }
            )
            OutlinedButton(onClick = requestLocation, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.create_alert_use_location))
            }
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = viewModel::requestConfirmation,
                enabled = state.canContinue,
                loading = state.publishing,
            )
        }
    }

    if (state.confirming) {
        val category = state.category
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(stringResource(R.string.create_alert_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.create_alert_confirm_body,
                        category?.let { stringResource(it.style().label) }.orEmpty(),
                    )
                )
            },
            confirmButton = { TextButton(onClick = viewModel::publish) { Text(stringResource(R.string.action_publish)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CategoryTile(category: AlertCategory, selected: Boolean, onClick: () -> Unit) {
    val style = category.style()
    Card(
        onClick = onClick,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.width(104.dp).heightIn(min = 96.dp).semantics { this.selected = selected },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(32.dp))
            Text(stringResource(style.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
