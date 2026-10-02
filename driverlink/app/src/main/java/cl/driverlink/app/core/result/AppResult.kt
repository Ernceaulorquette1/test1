package cl.driverlink.app.core.result

import kotlinx.coroutines.CancellationException

/** Errores de negocio comprensibles. Nunca se muestran excepciones técnicas al usuario. */
sealed interface AppError {
    data object Network : AppError
    data object Unauthenticated : AppError
    data object PermissionDenied : AppError
    data object NotFound : AppError
    data object PremiumRequired : AppError
    data object AlreadyExists : AppError
    data object AlreadyVoted : AppError
    data object InvalidCredentials : AppError
    data object EmailInUse : AppError
    data object WeakPassword : AppError
    data object RateLimited : AppError
    data object LocationUnavailable : AppError
    data object FeatureDisabled : AppError
    data object NotConfigured : AppError
    data object Maintenance : AppError
    data class Validation(val field: String) : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}

/** Excepción que transporta un [AppError] a través de capas que solo conocen Throwable. */
class AppException(val error: AppError) : Exception(error.toString())

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** Traduce excepciones de infraestructura (Firebase, red...) a [AppError]. */
fun interface ErrorMapper {
    fun map(throwable: Throwable): AppError
}

object DefaultErrorMapper : ErrorMapper {
    override fun map(throwable: Throwable): AppError = when (throwable) {
        is AppException -> throwable.error
        is java.io.IOException -> AppError.Network
        is SecurityException -> AppError.PermissionDenied
        else -> AppError.Unknown(throwable)
    }
}

/**
 * Ejecuta una operación y convierte cualquier excepción en [AppResult.Failure].
 * La cancelación de corrutinas se propaga siempre.
 */
suspend inline fun <T> safeCall(
    mapper: ErrorMapper = DefaultErrorMapper,
    crossinline block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(mapper.map(e))
}
