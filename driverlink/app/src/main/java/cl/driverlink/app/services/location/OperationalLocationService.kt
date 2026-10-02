package cl.driverlink.app.services.location

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cl.driverlink.app.DriverLinkApp
import cl.driverlink.app.R
import cl.driverlink.app.services.notifications.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que comparte ubicación SOLO mientras:
 *  - un SOS está activo (se detiene al resolverse o cancelarse), o
 *  - el conductor tiene una jornada empresarial iniciada.
 *
 * Nunca se usa para seguimiento permanente. Muestra una notificación persistente
 * para que el usuario sepa en todo momento que su ubicación se está compartiendo.
 */
class OperationalLocationService : LifecycleService() {

    private var trackingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as DriverLinkApp).container
        if (intent?.action == ACTION_STOP || !container.locationProvider.hasPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = intent?.getStringExtra(EXTRA_MODE)
        startForegroundCompat(mode)
        trackingJob?.cancel()
        trackingJob = when (mode) {
            MODE_SOS -> trackSos(intent.getStringExtra(EXTRA_SOS_ID).orEmpty())
            MODE_WORK -> trackWork(
                intent.getStringExtra(EXTRA_COMPANY_ID).orEmpty(),
                intent.getStringExtra(EXTRA_SESSION_ID).orEmpty(),
            )
            else -> { stopSelf(); null }
        }
        return START_NOT_STICKY
    }

    private fun trackSos(sosId: String): Job = lifecycleScope.launch {
        val container = (application as DriverLinkApp).container
        val interval = container.configRepository.config.value.sosLocationIntervalSeconds
        val updates = launch {
            container.locationProvider.updates(interval)
                .catch { stopSelf() }
                .collect { container.sosRepository.pushLocation(sosId, it) }
        }
        // Se detiene en cuanto el SOS deja de estar activo.
        container.sosRepository.observeSos(sosId).filter { it == null || !it.status.isActive }.first()
        updates.cancel()
        stopSelf()
    }

    private fun trackWork(companyId: String, sessionId: String): Job = lifecycleScope.launch {
        val container = (application as DriverLinkApp).container
        val interval = container.configRepository.config.value.workLocationIntervalSeconds
        val updates = launch {
            container.locationProvider.updates(interval, minDistanceMeters = 50f)
                .catch { stopSelf() }
                .collect { container.workSessionRepository.pushLocation(companyId, sessionId, it) }
        }
        container.workSessionRepository.observeActiveSession(companyId)
            .filter { it == null || it.id != sessionId }
            .first()
        updates.cancel()
        stopSelf()
    }

    private fun startForegroundCompat(mode: String?) {
        val text = if (mode == MODE_SOS) R.string.tracking_sos_text else R.string.tracking_work_text
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(getString(text))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "cl.driverlink.app.STOP_TRACKING"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SOS_ID = "sosId"
        private const val EXTRA_COMPANY_ID = "companyId"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val MODE_SOS = "sos"
        private const val MODE_WORK = "work"

        fun startForSos(context: Context, sosId: String) = start(context) {
            putExtra(EXTRA_MODE, MODE_SOS)
            putExtra(EXTRA_SOS_ID, sosId)
        }

        fun startForWorkSession(context: Context, companyId: String, sessionId: String) = start(context) {
            putExtra(EXTRA_MODE, MODE_WORK)
            putExtra(EXTRA_COMPANY_ID, companyId)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, OperationalLocationService::class.java).setAction(ACTION_STOP))
            } catch (e: IllegalStateException) {
                // App en segundo plano: el servicio ya se detiene solo al terminar el SOS/jornada.
            }
        }

        private fun start(context: Context, extras: Intent.() -> Unit) {
            val intent = Intent(context, OperationalLocationService::class.java).apply(extras)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // La app no está en primer plano: no se inicia seguimiento en segundo plano.
            } catch (e: SecurityException) {
                // Sin permiso de ubicación: el SOS sigue funcionando con la ubicación inicial.
            }
        }
    }
}
