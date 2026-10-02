package cl.driverlink.app.presentation.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.CompanyRole
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.usecase.CompanyField
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.EmptyView
import cl.driverlink.app.ui.components.PrimaryButton
import cl.driverlink.app.ui.components.StateContent

fun CompanyRole.labelRes(): Int = when (this) {
    CompanyRole.OWNER -> R.string.company_role_owner
    CompanyRole.ADMIN -> R.string.company_role_admin
    CompanyRole.SUPERVISOR -> R.string.company_role_supervisor
    CompanyRole.DISPATCHER -> R.string.company_role_dispatcher
    CompanyRole.DRIVER -> R.string.company_role_driver
}

fun MemberStatus.labelRes(): Int = when (this) {
    MemberStatus.INVITED -> R.string.member_status_invited
    MemberStatus.PENDING -> R.string.member_status_pending
    MemberStatus.ACTIVE -> R.string.member_status_active
    MemberStatus.SUSPENDED -> R.string.member_status_suspended
    MemberStatus.REMOVED -> R.string.member_status_removed
}

@Composable
fun CompanyEntryScreen(onBack: () -> Unit, onCreate: () -> Unit, onJoin: () -> Unit, onOpenCompany: (String) -> Unit) {
    val viewModel = appViewModel { CompanyEntryViewModel(it.companyRepository, it.configRepository) }
    val state by viewModel.memberships.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.company_entry_title), onBack = onBack) }) { padding ->
        if (!viewModel.enabled) {
            EmptyView(stringResource(R.string.company_features_disabled), Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.company_entry_intro), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(text = stringResource(R.string.company_join), onClick = onJoin)
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.company_create))
            }
            Text(stringResource(R.string.company_my_companies), style = MaterialTheme.typography.titleMedium)
            StateContent(state, emptyMessage = stringResource(R.string.company_none)) { memberships ->
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memberships, key = { it.company.id }) { m ->
                        val active = m.member.status == MemberStatus.ACTIVE
                        Card(onClick = { if (active) onOpenCompany(m.company.id) }, enabled = active, modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text(m.company.tradeName) },
                                supportingContent = {
                                    Text(stringResource(m.member.role.labelRes()) + " · " + stringResource(m.member.status.labelRes()))
                                },
                                leadingContent = { Icon(Icons.Filled.Business, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateCompanyScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val viewModel = appViewModel { CreateCompanyViewModel(it.createCompanyUseCase, it.analytics) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.company_create), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val d = state.draft
            CompanyInput(d.rut, R.string.company_rut, CompanyField.RUT in state.errors) { v -> viewModel.update { it.copy(rut = v) } }
            CompanyInput(d.legalName, R.string.company_legal_name, CompanyField.LEGAL_NAME in state.errors) { v -> viewModel.update { it.copy(legalName = v) } }
            CompanyInput(d.tradeName, R.string.company_trade_name, CompanyField.TRADE_NAME in state.errors) { v -> viewModel.update { it.copy(tradeName = v) } }
            CompanyInput(d.email, R.string.field_email, CompanyField.EMAIL in state.errors, KeyboardType.Email) { v -> viewModel.update { it.copy(email = v) } }
            CompanyInput(d.phone, R.string.field_phone, CompanyField.PHONE in state.errors, KeyboardType.Phone) { v -> viewModel.update { it.copy(phone = v) } }
            CompanyInput(d.address, R.string.company_address, CompanyField.ADDRESS in state.errors) { v -> viewModel.update { it.copy(address = v) } }
            Text(stringResource(R.string.company_create_note), style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(text = stringResource(R.string.company_create), onClick = viewModel::submit, loading = state.saving)
        }
    }
}

@Composable
private fun CompanyInput(value: String, label: Int, isError: Boolean, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(120)) },
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(R.string.error_field_invalid)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun JoinCompanyScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { JoinCompanyViewModel(it.joinCompanyUseCase, it.analytics) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.company_join), onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.sent) {
                Text(stringResource(R.string.join_request_sent), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.join_request_sent_body))
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                return@Column
            }
            Text(stringResource(R.string.join_intro))
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCode,
                label = { Text(stringResource(R.string.join_code)) },
                placeholder = { Text("DL-ABC-8492") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(text = stringResource(R.string.join_submit), onClick = viewModel::submit, loading = state.sending)
        }
    }
}
