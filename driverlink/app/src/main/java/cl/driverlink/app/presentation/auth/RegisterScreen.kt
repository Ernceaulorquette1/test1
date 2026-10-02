package cl.driverlink.app.presentation.auth

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.usecase.RegistrationField
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.PrimaryButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { RegisterViewModel(it.registerUseCase, it.analytics) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.update { it.copy(photoUri = uri.toString()) }
    }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.register_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(state.photoUri, "${state.firstName.take(1)}${state.lastName.take(1)}", size = 64.dp)
                OutlinedButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.register_photo_optional)) }
            }
            Field(state.firstName, { v -> viewModel.update { it.copy(firstName = v) } }, R.string.field_first_name,
                RegistrationField.FIRST_NAME in state.fieldErrors, R.string.error_name_invalid)
            Field(state.lastName, { v -> viewModel.update { it.copy(lastName = v) } }, R.string.field_last_name,
                RegistrationField.LAST_NAME in state.fieldErrors, R.string.error_name_invalid)
            Field(state.email, { v -> viewModel.update { it.copy(email = v) } }, R.string.field_email,
                RegistrationField.EMAIL in state.fieldErrors, R.string.error_email_invalid, KeyboardType.Email)
            Field(state.phone, { v -> viewModel.update { it.copy(phone = v) } }, R.string.field_phone,
                RegistrationField.PHONE in state.fieldErrors, R.string.error_phone_invalid, KeyboardType.Phone)
            OutlinedTextField(
                value = state.password,
                onValueChange = { v -> viewModel.update { it.copy(password = v) } },
                label = { Text(stringResource(R.string.field_password)) },
                supportingText = { Text(stringResource(R.string.password_rules)) },
                isError = RegistrationField.PASSWORD in state.fieldErrors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Field(state.city, { v -> viewModel.update { it.copy(city = v) } }, R.string.field_city,
                RegistrationField.CITY in state.fieldErrors, R.string.error_required)
            Field(state.commune, { v -> viewModel.update { it.copy(commune = v) } }, R.string.field_commune,
                RegistrationField.COMMUNE in state.fieldErrors, R.string.error_required)

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
            if (RegistrationField.PLATFORMS in state.fieldErrors) {
                Text(stringResource(R.string.error_platforms_required), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.platform_disclaimer), style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.acceptedTerms, onCheckedChange = { v -> viewModel.update { it.copy(acceptedTerms = v) } })
                Text(stringResource(R.string.register_accept_terms), style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(text = stringResource(R.string.register_submit), onClick = viewModel::submit, loading = state.loading)
            Text("", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    isError: Boolean,
    errorText: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(errorText)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
