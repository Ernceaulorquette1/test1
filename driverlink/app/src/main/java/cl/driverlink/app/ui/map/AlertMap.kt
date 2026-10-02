package cl.driverlink.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.driverlink.app.BuildConfig
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.ui.components.AlertCard
import cl.driverlink.app.ui.theme.style
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Mapa de alertas. Es el ÚNICO archivo que conoce al proveedor de mapas:
 * cambiar Google Maps por otro proveedor solo requiere reimplementar este composable.
 *
 * Solo dibuja alertas y la ubicación propia (si hay permiso); nunca la ubicación
 * de otros conductores.
 */
@Composable
fun AlertMap(
    alerts: List<Alert>,
    center: GeoPoint,
    myLocationEnabled: Boolean,
    onAlertClick: (Alert) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.MAPS_CONFIGURED) {
        AlertListFallback(alerts, onAlertClick, modifier)
        return
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(center.latitude, center.longitude), 12f)
    }
    LaunchedEffect(center) {
        cameraState.animate(CameraUpdateFactory.newLatLng(LatLng(center.latitude, center.longitude)))
    }
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
        uiSettings = MapUiSettings(myLocationButtonEnabled = myLocationEnabled, zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        alerts.forEach { alert ->
            val style = alert.category.style()
            val markerState = remember(alert.id) { MarkerState(LatLng(alert.location.latitude, alert.location.longitude)) }
            Marker(
                state = markerState,
                title = stringResource(style.label),
                snippet = alert.description,
                icon = BitmapDescriptorFactory.defaultMarker(style.markerHue),
                onClick = {
                    onAlertClick(alert)
                    true
                },
            )
        }
    }
}

/** Vista alternativa cuando no hay MAPS_API_KEY configurada (p. ej. demo sin clave). */
@Composable
private fun AlertListFallback(alerts: List<Alert>, onAlertClick: (Alert) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
            Text(
                stringResource(R.string.map_not_configured),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 200.dp)) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(alert, onClick = { onAlertClick(alert) }, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}
