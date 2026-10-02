package cl.driverlink.app.presentation.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R

@Composable
fun SplashScreen() {
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.SettingsVoice, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.brand_name_upper), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.brand_tagline), color = MaterialTheme.colorScheme.onPrimary)
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun MaintenanceScreen() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.maintenance_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.maintenance_body), textAlign = TextAlign.Center)
        }
    }
}
