package cl.driverlink.app.data.firebase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.ErrorMapper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException

/** Traduce excepciones de Firebase a errores de negocio comprensibles. */
object FirebaseErrorMapper : ErrorMapper {
    override fun map(throwable: Throwable): AppError = when (throwable) {
        is AppException -> throwable.error
        is FirebaseNetworkException -> AppError.Network
        is FirebaseTooManyRequestsException -> AppError.RateLimited
        is FirebaseAuthWeakPasswordException -> AppError.WeakPassword
        is FirebaseAuthUserCollisionException -> AppError.EmailInUse
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> AppError.InvalidCredentials
        is FirebaseFirestoreException -> when (throwable.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.Unauthenticated
            FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
            FirebaseFirestoreException.Code.ALREADY_EXISTS -> AppError.AlreadyExists
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Network
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.RateLimited
            else -> AppError.Unknown(throwable)
        }
        is FirebaseFunctionsException -> mapFunctions(throwable)
        is StorageException -> when (throwable.errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED -> AppError.PermissionDenied
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> AppError.Network
            else -> AppError.Unknown(throwable)
        }
        is java.io.IOException -> AppError.Network
        else -> AppError.Unknown(throwable)
    }

    /** Las Cloud Functions envían un motivo legible por máquina en el mensaje del HttpsError. */
    private fun mapFunctions(e: FirebaseFunctionsException): AppError = when (e.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> AppError.Unauthenticated
        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            if (e.message?.contains("premium-required") == true) AppError.PremiumRequired else AppError.PermissionDenied
        FirebaseFunctionsException.Code.NOT_FOUND -> AppError.NotFound
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> AppError.AlreadyExists
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> AppError.Validation(e.message ?: "")
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> when {
            e.message?.contains("feature-disabled") == true -> AppError.FeatureDisabled
            e.message?.contains("not-configured") == true -> AppError.NotConfigured
            else -> AppError.PermissionDenied
        }
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AppError.RateLimited
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AppError.Network
        else -> AppError.Unknown(e)
    }
}
