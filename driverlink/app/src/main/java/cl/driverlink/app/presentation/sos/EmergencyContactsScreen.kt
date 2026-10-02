package cl.driverlink.app.presentation.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.DefaultErrorMapper
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toListUiState
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.repository.EmergencyContactRepository
import cl.driverlink.app.domain.validation.Validators
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.subscription.PremiumGate
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class EmergencyContactsViewModel(private val repository: EmergencyContactRepository) : ViewModel() {

    val contacts: StateFlow<UiState<List<EmergencyContact>>> = repository.observeContacts()
        .map { it.toListUiState() }
        .catch { emit(UiState.Error(DefaultErrorMapper.map(it).toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** Devuelve false si los datos no son válidos (el diálogo permanece abierto). */
    fun save(name: String, relationship: String, phone: String): Boolean {
        val normalized = Validators.normalizeChileanPhone(phone)
        if (name.isBlank() || normalized == null) {
            _message.value = UiText.Res(R.string.contacts_invalid)
            return false
        }
        viewModelScope.launch {
            val result = repository.saveContact(
                EmergencyContact(UUID.randomUUID().toString(), name.trim(), relationship.trim(), normalized)
            )
            _message.value = if (result is AppResult.Failure) result.error.toUiText() else null
        }
        return true
    }

    fun delete(contact: EmergencyContact) {
        viewModelScope.launch {
            val result = repository.deleteContact(contact.id)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }
}

@Composable
fun EmergencyContactsScreen(onBack: () -> Unit, onSeePlans: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { DriverLinkTopBar(stringResource(R.string.emergency_contacts_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PremiumGate(
                feature = PremiumFeature.EMERGENCY_CONTACTS,
                lockedTitle = stringResource(R.string.contacts_locked_title),
                onSeePlans = onSeePlans,
            ) {
                ContactsContent(adding = adding, onAddingChange = { adding = it })
            }
        }
    }
}

@Composable
private fun ContactsContent(adding: Boolean, onAddingChange: (Boolean) -> Unit) {
    val viewModel = appViewModel { EmergencyContactsViewModel(it.emergencyContactRepository) }
    val state by viewModel.contacts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddingChange(true) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.contacts_add)) },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            Text(stringResource(R.string.contacts_explanation), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            StateContent(state, emptyMessage = stringResource(R.string.contacts_empty)) { contacts ->
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text("${contact.relationship} · ${contact.phone}") },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(contact) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var relationship by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onAddingChange(false) },
            title = { Text(stringResource(R.string.contacts_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.contacts_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(relationship, { relationship = it.take(40) }, label = { Text(stringResource(R.string.contacts_relationship)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        phone, { phone = it.take(20) }, label = { Text(stringResource(R.string.field_phone)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { if (viewModel.save(name, relationship, phone)) onAddingChange(false) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { onAddingChange(false) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
