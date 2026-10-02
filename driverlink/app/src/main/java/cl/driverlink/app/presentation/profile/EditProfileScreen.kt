package cl.driverlink.app.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.components.PrimaryButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { EditProfileViewModel(it.userRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.update { it.copy(newPhotoUri = uri.toString()) }
    }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.profile_edit), onBack = onBack) }) { padding ->
        if (!state.loaded) {
            LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(state.newPhotoUri ?: state.photoUrl, "${state.firstName.take(1)}${state.lastName.take(1)}", size = 72.dp)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.profile_change_photo))
                }
            }
            TextInput(state.firstName, R.string.field_first_name) { v -> viewModel.update { it.copy(firstName = v) } }
            TextInput(state.lastName, R.string.field_last_name) { v -> viewModel.update { it.copy(lastName = v) } }
            TextInput(state.phone, R.string.field_phone, KeyboardType.Phone) { v -> viewModel.update { it.copy(phone = v) } }
            TextInput(state.city, R.string.field_city) { v -> viewModel.update { it.copy(city = v) } }
            TextInput(state.commune, R.string.field_commune) { v -> viewModel.update { it.copy(commune = v) } }
            Text(stringResource(R.string.register_platforms), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = platform in state.platforms,
                        onClick = { viewModel.togglePlatform(platform) },
                        label = { Text(platform.displayName) },
                    )
                }
            }
            Text(stringResource(R.string.profile_email_readonly), style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(text = stringResource(R.string.action_save), onClick = viewModel::save, loading = state.saving)
        }
    }
}

@Composable
private fun TextInput(value: String, label: Int, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
