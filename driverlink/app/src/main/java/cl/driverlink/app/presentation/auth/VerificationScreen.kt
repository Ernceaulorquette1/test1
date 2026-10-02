package cl.driverlink.app.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.VerificationStatus
import cl.driverlink.app.domain.repository.UserRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerificationUiState(
    val status: VerificationStatus = VerificationStatus.UNVERIFIED,
    val evidence: List<String> = emptyList(),
    val notes: String = "",
    val sending: Boolean = false,
    val message: UiText? = null,
)

class VerificationViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val form = MutableStateFlow(VerificationUiState())

    val state: StateFlow<VerificationUiState> = combine(form, userRepository.observeCurrentUser()) { f, user ->
        f.copy(status = user?.verificationStatus ?: VerificationStatus.UNVERIFIED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationUiState())

    fun addEvidence(uris: List<String>) = form.update { it.copy(evidence = (it.evidence + uris).distinct().take(MAX_FILES)) }
    fun clearEvidence() = form.update { it.copy(evidence = emptyList()) }
    fun onNotes(value: String) = form.update { it.copy(notes = value.take(500)) }

    fun submit() {
        val current = form.value
        if (current.evidence.isEmpty()) {
            form.update { it.copy(message = UiText.Res(R.string.verification_need_evidence)) }
            return
        }
        form.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            val result = userRepository.submitVerification(current.evidence, current.notes)
            form.update {
                when (result) {
                    is AppResult.Success -> it.copy(sending = false, evidence = emptyList(), message = UiText.Res(R.string.verification_sent))
                    is AppResult.Failure -> it.copy(sending = false, message = result.error.toUiText())
                }
            }
        }
    }

    private companion object { const val MAX_FILES = 3 }
}

fun VerificationStatus.labelRes(): Int = when (this) {
    VerificationStatus.UNVERIFIED -> R.string.verification_unverified
    VerificationStatus.PENDING -> R.string.verification_pending
    VerificationStatus.VERIFIED -> R.string.verification_verified
    VerificationStatus.REJECTED -> R.string.verification_rejected
    VerificationStatus.SUSPENDED -> R.string.verification_suspended
}

@Composable
fun VerificationScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { VerificationViewModel(it.userRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addEvidence(uris.map { it.toString() })
    }
    val canSubmit = state.status == VerificationStatus.UNVERIFIED || state.status == VerificationStatus.REJECTED

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.verification_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.verification_current_status), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(state.status.labelRes()), style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(stringResource(R.string.verification_explanation))
            Text(stringResource(R.string.verification_privacy), style = MaterialTheme.typography.bodySmall)
            if (canSubmit) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.verification_attach))
                }
                if (state.evidence.isNotEmpty()) {
                    Text(pluralStringResource(R.plurals.verification_files, state.evidence.size, state.evidence.size))
                }
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text(stringResource(R.string.verification_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                PrimaryButton(text = stringResource(R.string.verification_submit), onClick = viewModel::submit, loading = state.sending)
            }
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
        }
    }
}
