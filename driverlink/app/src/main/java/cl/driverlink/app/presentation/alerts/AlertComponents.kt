package cl.driverlink.app.presentation.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.ui.components.relativeTimeText
import cl.driverlink.app.ui.theme.style

/** Encabezado de alerta: categoría, descripción, tiempo y confirmaciones. */
@Composable
fun AlertSummary(alert: Alert) {
    val style = alert.category.style()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(28.dp))
            Text(stringResource(style.label), style = MaterialTheme.typography.titleLarge)
        }
        if (alert.description.isNotBlank()) Text(alert.description, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.alert_reported_ago, relativeTimeText(alert.createdAt)) + " · " +
                pluralStringResource(R.plurals.alert_confirmations, alert.confirmationsCount, alert.confirmationsCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Botones CONFIRMAR / YA TERMINÓ / INCORRECTO. Se deshabilitan tras votar. */
@Composable
fun AlertVoteButtons(
    myVote: AlertVote?,
    onVote: (AlertVote) -> Unit,
    isOwnAlert: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isOwnAlert) {
        Text(stringResource(R.string.alert_own), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    if (myVote != null) {
        Text(stringResource(R.string.alert_already_voted), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onVote(AlertVote.CONFIRM) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text(stringResource(R.string.alert_vote_confirm), modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onVote(AlertVote.ENDED) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.DoneAll, contentDescription = null)
                Text(stringResource(R.string.alert_vote_ended), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = { onVote(AlertVote.INCORRECT) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.ThumbDown, contentDescription = null)
                Text(stringResource(R.string.alert_vote_incorrect), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
