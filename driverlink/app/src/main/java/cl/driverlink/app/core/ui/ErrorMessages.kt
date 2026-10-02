package cl.driverlink.app.core.ui

import cl.driverlink.app.R
import cl.driverlink.app.core.result.AppError

/** Traduce errores de negocio a mensajes comprensibles. */
fun AppError.toUiText(): UiText = UiText.Res(
    when (this) {
        AppError.Network -> R.string.error_network
        AppError.Unauthenticated -> R.string.error_unauthenticated
        AppError.PermissionDenied -> R.string.error_permission_denied
        AppError.NotFound -> R.string.error_not_found
        AppError.PremiumRequired -> R.string.error_premium_required
        AppError.AlreadyExists -> R.string.error_already_exists
        AppError.AlreadyVoted -> R.string.error_already_voted
        AppError.InvalidCredentials -> R.string.error_invalid_credentials
        AppError.EmailInUse -> R.string.error_email_in_use
        AppError.WeakPassword -> R.string.error_weak_password
        AppError.RateLimited -> R.string.error_rate_limited
        AppError.LocationUnavailable -> R.string.error_location_unavailable
        AppError.FeatureDisabled -> R.string.error_feature_disabled
        AppError.NotConfigured -> R.string.error_not_configured
        AppError.Maintenance -> R.string.error_maintenance
        is AppError.Validation -> R.string.error_validation
        is AppError.Unknown -> R.string.error_unknown
    }
)
