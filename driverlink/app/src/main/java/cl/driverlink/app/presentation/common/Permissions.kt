package cl.driverlink.app.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import cl.driverlink.app.R

object AppPermissions {
    val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val MICROPHONE = arrayOf(Manifest.permission.RECORD_AUDIO)
    val NOTIFICATIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun anyGranted(context: Context, permissions: Array<String>): Boolean =
        permissions.isEmpty() || permissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}

/**
 * Solicitud de permisos con explicación previa (por qué se necesita).
 * Devuelve una función que inicia el flujo; [onResult] recibe si se concedió.
 */
@Composable
fun rememberPermissionRequest(
    permissions: Array<String>,
    rationale: String,
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult(result.values.any { it })
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; onResult(false) },
            title = { Text(stringResource(R.string.permission_title)) },
            text = { Text(rationale) },
            confirmButton = {
                TextButton(onClick = { showRationale = false; launcher.launch(permissions) }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; onResult(false) }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }
    return {
        if (AppPermissions.anyGranted(context, permissions)) onResult(true) else showRationale = true
    }
}
