package cl.driverlink.app.presentation.sos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.OfficialService

/** Abre el marcador con el número (no llama automáticamente: el usuario confirma). */
fun dial(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        // Dispositivo sin marcador (tablet): no hay acción posible.
    }
}

/**
 * Acceso directo a servicios oficiales. DriverLink no reemplaza a policía,
 * ambulancia ni bomberos: este bloque está visible en todo el flujo SOS.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfficialServicesBlock(services: List<OfficialService>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sos_official_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sos_official_disclaimer), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            services.forEach { service ->
                OutlinedButton(onClick = { dial(context, service.number) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Text(" ${service.name} ${service.number}")
                }
            }
        }
    }
}
