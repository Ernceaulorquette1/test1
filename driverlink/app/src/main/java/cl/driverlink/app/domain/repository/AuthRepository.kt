package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.RegistrationData
import kotlinx.coroutines.flow.Flow

sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String) : AuthState
}

interface AuthRepository {
    val authState: Flow<AuthState>
    fun currentUserId(): String?
    suspend fun signIn(email: String, password: String): AppResult<Unit>
    suspend fun register(data: RegistrationData): AppResult<Unit>
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun signOut()
    /** Elimina la cuenta. La limpieza de datos la completa el servidor. */
    suspend fun deleteAccount(): AppResult<Unit>
}
