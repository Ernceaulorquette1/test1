package cl.driverlink.app.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.GeoPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

interface LocationProvider {
    fun hasPermission(): Boolean
    /** Ubicación puntual (mapa, crear alerta, SOS). */
    suspend fun currentLocation(): AppResult<GeoPoint>
    /** Actualizaciones periódicas: solo para SOS activo o jornada empresarial. */
    fun updates(intervalSeconds: Int, minDistanceMeters: Float = 25f): Flow<GeoPoint>
}

/**
 * Implementación con Fused Location Provider. [fallback] solo se usa en el flavor demo
 * (emuladores sin GPS); en producción es null y se informa el error.
 */
class FusedLocationProvider(
    private val context: Context,
    private val fallback: GeoPoint? = null,
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): AppResult<GeoPoint> {
        if (!hasPermission()) return fallbackOr(AppError.PermissionDenied)
        return try {
            val token = CancellationTokenSource()
            val location = withTimeoutOrNull(10_000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            } ?: client.lastLocation.await()
            if (location != null) AppResult.Success(GeoPoint(location.latitude, location.longitude))
            else fallbackOr(AppError.LocationUnavailable)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallbackOr(AppError.LocationUnavailable)
        }
    }

    @SuppressLint("MissingPermission")
    override fun updates(intervalSeconds: Int, minDistanceMeters: Float): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("Sin permiso de ubicación"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalSeconds * 1000L)
            .setMinUpdateIntervalMillis(intervalSeconds * 500L)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun fallbackOr(error: AppError): AppResult<GeoPoint> =
        fallback?.let { AppResult.Success(it) } ?: AppResult.Failure(error)
}
