package cl.driverlink.app.domain.repository

import cl.driverlink.app.domain.model.AppConfig
import kotlinx.coroutines.flow.StateFlow

interface ConfigRepository {
    val config: StateFlow<AppConfig>
    /** Descarga la configuración remota; si falla se mantienen los valores por defecto. */
    suspend fun refresh()
}
