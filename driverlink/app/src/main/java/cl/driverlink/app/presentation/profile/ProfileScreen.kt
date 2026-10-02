package cl.driverlink.app.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.presentation.auth.labelRes
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.subscription.labelRes
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.components.formatDate

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onSubscription: () -> Unit,
    onEmergencyContacts: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onVerification: () -> Unit,
    onCompany: () -> Unit,
) {
    val viewModel = appViewModel { ProfileViewModel(it.userRepository, it.subscriptionRepository, it.authRepository, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.profile_title)) }) { padding ->
        StateContent(state, emptyMessage = "", modifier = Modifier.padding(padding)) { data ->
            val user = data.user
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Avatar(user.photoUrl, user.initials, size = 96.dp, contentDescription = stringResource(R.string.profile_photo))
                        Text(user.displayName, style = MaterialTheme.typography.headlineMedium)
                        Text("${user.commune}, ${user.city}")
                        Text(user.platforms.joinToString(" · ") { it.displayName }, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onVerification,
                                label = { Text(stringResource(user.verificationStatus.labelRes())) },
                                leadingIcon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                            )
                            AssistChip(
                                onClick = onSubscription,
                                label = { Text(stringResource(data.plan.labelRes())) },
                                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            )
                        }
                        Text(stringResource(R.string.profile_member_since, formatDate(user.createdAt)), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(R.string.profile_reputation, user.reputation.alertsCreated, user.reputation.alertsConfirmed),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    HorizontalDivider()
                }
                item { Option(Icons.Filled.Edit, R.string.profile_edit, onEdit) }
                item { Option(Icons.Filled.Star, R.string.profile_subscription, onSubscription) }
                item { Option(Icons.Filled.ContactPhone, R.string.emergency_contacts_title, onEmergencyContacts) }
                item { Option(Icons.Filled.Notifications, R.string.notifications_title, onNotifications) }
                item { Option(Icons.Filled.Lock, R.string.privacy_title, onPrivacy) }
                item { Option(Icons.Filled.Business, R.string.space_company, onCompany) }
                item { Option(Icons.AutoMirrored.Filled.Help, R.string.profile_help) { showHelp = true } }
                item { Option(Icons.AutoMirrored.Filled.ExitToApp, R.string.profile_sign_out, viewModel::signOut) }
                item { Option(Icons.Filled.DeleteForever, R.string.profile_delete_account) { confirmDelete = true } }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_account)) },
            text = { Text(stringResource(R.string.profile_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.profile_help)) },
            text = { Text(stringResource(R.string.help_body)) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun Option(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
