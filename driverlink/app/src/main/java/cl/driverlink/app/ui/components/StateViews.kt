package cl.driverlink.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R
import cl.driverlink.app.core.ui.UiState

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.state_loading)
    Box(modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
        Text(label, modifier = Modifier.padding(top = 72.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    MessageView(message = message, icon = { Icon(Icons.Filled.Inbox, null, Modifier.size(48.dp)) }, modifier = modifier)
}

@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    MessageView(
        message = message,
        icon = { Icon(Icons.Filled.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        action = onRetry?.let { retry -> { OutlinedButton(onClick = retry) { Text(stringResource(R.string.action_retry)) } } },
        modifier = modifier,
    )
}

@Composable
private fun MessageView(
    message: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Renderiza los cuatro estados estándar de una pantalla. */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> LoadingView(modifier)
        UiState.Empty -> EmptyView(emptyMessage, modifier)
        is UiState.Error -> ErrorView(state.message.asString(), onRetry, modifier)
        is UiState.Success -> content(state.data)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.offline_banner),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
