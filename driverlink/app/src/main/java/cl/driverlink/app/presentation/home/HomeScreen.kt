package cl.driverlink.app.presentation.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.ui.components.AlertCard
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.theme.DriverLinkThemeExt

@Composable
fun HomeScreen(
    onOpenMap: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenChat: () -> Unit,
    onReport: () -> Unit,
    onSos: () -> Unit,
    onAlert: (String) -> Unit,
    onChannel: (String) -> Unit,
    onVerify: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.userRepository, it.alertRepository, it.channelRepository, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Permiso de notificaciones (Android 13+) solicitado una vez, con explicación.
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberPermissionRequest(
        AppPermissions.NOTIFICATIONS,
        stringResource(R.string.permission_notifications_rationale),
    ) { }
    LaunchedEffect(Unit) {
        if (!askedNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotifications = true
            requestNotifications()
        }
    }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.brand_name)) }) { padding ->
        StateContent(state, emptyMessage = "", modifier = Modifier.padding(padding)) { data ->
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(stringResource(R.string.home_greeting, data.user.firstName), style = MaterialTheme.typography.headlineMedium)
                    Text(data.user.city.ifBlank { stringResource(R.string.home_no_city) }, style = MaterialTheme.typography.bodyLarge)
                }
                item {
                    Card(
                        onClick = onOpenMap,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                pluralStringResource(R.plurals.home_active_alerts, data.activeAlerts, data.activeAlerts),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(stringResource(R.string.home_active_alerts_hint))
                        }
                    }
                }
                if (data.needsVerification) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.home_verify_title)) },
                            supportingContent = { Text(stringResource(R.string.home_verify_body)) },
                            leadingContent = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                            trailingContent = { TextButton(onClick = onVerify) { Text(stringResource(R.string.action_verify)) } },
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(Icons.Filled.Map, R.string.tab_map, onOpenMap, Modifier.weight(1f))
                        QuickAction(Icons.Filled.SettingsVoice, R.string.tab_radio, onOpenRadio, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(Icons.AutoMirrored.Filled.Chat, R.string.tab_chat, onOpenChat, Modifier.weight(1f))
                        QuickAction(Icons.Filled.AddAlert, R.string.action_report, onReport, Modifier.weight(1f))
                    }
                }
                item {
                    val colors = DriverLinkThemeExt.colors
                    Button(
                        onClick = onSos,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.sos, contentColor = colors.onSos),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) {
                        Text(stringResource(R.string.home_sos_button), fontWeight = FontWeight.Black)
                    }
                }
                item { SectionTitle(stringResource(R.string.home_recent_alerts)) }
                if (data.recentAlerts.isEmpty()) {
                    item { Text(stringResource(R.string.alerts_empty), style = MaterialTheme.typography.bodyMedium) }
                }
                items(data.recentAlerts, key = { it.id }) { alert -> AlertCard(alert, onClick = { onAlert(alert.id) }) }
                item { SectionTitle(stringResource(R.string.home_active_channels)) }
                items(data.channels, key = { it.id }) { channel ->
                    Card(onClick = { onChannel(channel.id) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(channel.name) },
                            supportingContent = { Text(channel.description) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun QuickAction(icon: ImageVector, label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.heightIn(min = 88.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(label).uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}
