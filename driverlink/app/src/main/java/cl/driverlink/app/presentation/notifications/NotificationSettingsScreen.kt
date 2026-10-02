package cl.driverlink.app.presentation.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.NotificationCategory
import cl.driverlink.app.domain.model.NotificationPreferences
import cl.driverlink.app.domain.repository.NotificationPreferencesRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.LoadingView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(private val repository: NotificationPreferencesRepository) : ViewModel() {
    val preferences: StateFlow<NotificationPreferences?> = repository.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(category: NotificationCategory, enabled: Boolean) {
        val current = preferences.value ?: return
        viewModelScope.launch { repository.updatePreferences(current.with(category, enabled)) }
    }
}

private fun NotificationCategory.titleRes(): Int = when (this) {
    NotificationCategory.NEARBY_ALERTS -> R.string.notif_nearby_alerts
    NotificationCategory.REPLIES -> R.string.notif_replies
    NotificationCategory.CHANNEL_ACTIVITY -> R.string.notif_channel_activity
    NotificationCategory.SOS_UPDATES -> R.string.notif_sos_updates
    NotificationCategory.ADMIN -> R.string.notif_admin
    NotificationCategory.SUBSCRIPTION -> R.string.notif_subscription
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { NotificationSettingsViewModel(it.notificationPreferencesRepository) }
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.notifications_title), onBack = onBack) }) { padding ->
        val prefs = preferences
        if (prefs == null) {
            LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.notifications_explanation), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            NotificationCategory.entries.forEach { category ->
                val enabled = prefs.isEnabled(category)
                ListItem(
                    headlineContent = { Text(stringResource(category.titleRes())) },
                    trailingContent = { Switch(checked = enabled, onCheckedChange = { viewModel.toggle(category, it) }) },
                )
            }
        }
    }
}
