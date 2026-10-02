package cl.driverlink.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.BlockedUser
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrivacyViewModel(private val moderationRepository: ModerationRepository) : ViewModel() {
    val blocked: StateFlow<List<BlockedUser>> = moderationRepository.observeBlockedUsers()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unblock(user: BlockedUser) {
        viewModelScope.launch { moderationRepository.unblockUser(user.userId) }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { PrivacyViewModel(it.moderationRepository) }
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.privacy_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.privacy_what_is_public_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_what_is_public))
            Text(stringResource(R.string.privacy_never_public_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_never_public))
            Text(stringResource(R.string.privacy_location_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_location))
            HorizontalDivider()
            Text(stringResource(R.string.privacy_blocked_users), style = MaterialTheme.typography.titleMedium)
            if (blocked.isEmpty()) Text(stringResource(R.string.privacy_no_blocked))
            blocked.forEach { user ->
                ListItem(
                    headlineContent = { Text(user.displayName) },
                    supportingContent = { Text(stringResource(R.string.privacy_blocked_since, formatDate(user.blockedAt))) },
                    trailingContent = { TextButton(onClick = { viewModel.unblock(user) }) { Text(stringResource(R.string.action_unblock)) } },
                )
            }
        }
    }
}
