package cl.driverlink.app.domain.auth

import cl.driverlink.app.core.result.AppResult

/**
 * Punto de extensión para futuras integraciones OAuth con plataformas de transporte.
 *
 * Ninguna integración oficial está aprobada actualmente, por lo que el registro
 * se realiza con correo y contraseña. Cuando una plataforma otorgue acceso a su API,
 * se implementa esta interfaz y se registra en [ExternalAuthRegistry].
 */
interface ExternalAuthProvider {
    val id: String
    val displayName: String
    /** Inicia el flujo OAuth (Custom Tabs + PKCE) y devuelve un token de Firebase personalizado. */
    suspend fun authenticate(): AppResult<String>
}

class ExternalAuthRegistry(private val providers: List<ExternalAuthProvider> = emptyList()) {
    fun available(): List<ExternalAuthProvider> = providers
}
