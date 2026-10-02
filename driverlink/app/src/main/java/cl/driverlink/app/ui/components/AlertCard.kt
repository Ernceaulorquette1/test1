package cl.driverlink.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.ui.theme.style

@Composable
fun AlertCard(alert: Alert, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = alert.category.style()
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(style.label), style = MaterialTheme.typography.titleMedium)
                if (alert.description.isNotBlank()) {
                    Text(alert.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${alert.commune.ifBlank { alert.city }} · ${relativeTimeText(alert.createdAt)} · " +
                        pluralStringResource(R.plurals.alert_confirmations, alert.confirmationsCount, alert.confirmationsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
