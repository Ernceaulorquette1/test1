package cl.driverlink.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R
import cl.driverlink.app.presentation.common.appContainer
import cl.driverlink.app.ui.components.PrimaryButton

@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    var showProviders by remember { mutableStateOf(false) }
    val externalProviders = appContainer().externalAuthRegistry.available()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.SettingsVoice, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.brand_name_upper), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            PrimaryButton(text = stringResource(R.string.welcome_login), onClick = onLogin)
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.welcome_register))
            }
            TextButton(onClick = { showProviders = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.welcome_external))
            }
            Text(
                stringResource(R.string.welcome_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showProviders) {
        AlertDialog(
            onDismissRequest = { showProviders = false },
            title = { Text(stringResource(R.string.welcome_external)) },
            text = {
                // No existe integración oficial aprobada: se informa con transparencia.
                Text(
                    if (externalProviders.isEmpty()) stringResource(R.string.external_auth_unavailable)
                    else externalProviders.joinToString("\n") { it.displayName }
                )
            },
            confirmButton = {
                TextButton(onClick = { showProviders = false; onRegister() }) { Text(stringResource(R.string.welcome_register)) }
            },
            dismissButton = { TextButton(onClick = { showProviders = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
