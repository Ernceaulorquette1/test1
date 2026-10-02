package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.ProfileUpdate
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.VerificationRequest
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Perfil del usuario autenticado; emite null si no hay sesión o aún no existe. */
    fun observeCurrentUser(): Flow<User?>
    suspend fun updateProfile(update: ProfileUpdate): AppResult<Unit>
    fun observeVerification(): Flow<VerificationRequest?>
    /** Envía evidencia privada de verificación (solo visible para el equipo autorizado). */
    suspend fun submitVerification(evidenceUris: List<String>, notes: String): AppResult<Unit>
}
