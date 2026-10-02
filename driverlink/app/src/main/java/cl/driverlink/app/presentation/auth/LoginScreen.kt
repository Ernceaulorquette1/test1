package cl.driverlink.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.PrimaryButton

@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit) {
    val viewModel = appViewModel { LoginViewModel(it.loginUseCase, it.authRepository, it.analytics) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.login_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.field_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.field_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            state.info?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
            PrimaryButton(text = stringResource(R.string.login_submit), onClick = viewModel::submit, loading = state.loading)
            TextButton(onClick = viewModel::resetPassword) { Text(stringResource(R.string.login_forgot)) }
            TextButton(onClick = onRegister) { Text(stringResource(R.string.login_no_account)) }
            if (cl.driverlink.app.BuildConfig.DEMO_MODE) {
                Text(stringResource(R.string.demo_credentials_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
