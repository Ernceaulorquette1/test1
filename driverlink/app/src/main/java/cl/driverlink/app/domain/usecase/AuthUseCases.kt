package cl.driverlink.app.domain.usecase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.validation.Validators

/** Campos del formulario de registro que pueden fallar la validación. */
enum class RegistrationField { FIRST_NAME, LAST_NAME, EMAIL, PHONE, PASSWORD, CITY, COMMUNE, PLATFORMS }

object RegistrationValidator {
    fun validate(data: RegistrationData): Set<RegistrationField> = buildSet {
        if (!Validators.isValidName(data.firstName)) add(RegistrationField.FIRST_NAME)
        if (!Validators.isValidName(data.lastName)) add(RegistrationField.LAST_NAME)
        if (!Validators.isValidEmail(data.email)) add(RegistrationField.EMAIL)
        if (!Validators.isValidPhone(data.phone)) add(RegistrationField.PHONE)
        if (!Validators.isValidPassword(data.password)) add(RegistrationField.PASSWORD)
        if (data.city.isBlank()) add(RegistrationField.CITY)
        if (data.commune.isBlank()) add(RegistrationField.COMMUNE)
        if (data.platforms.isEmpty()) add(RegistrationField.PLATFORMS)
    }
}

class RegisterUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(data: RegistrationData): AppResult<Unit> {
        val errors = RegistrationValidator.validate(data)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors.first().name))
        val normalized = data.copy(
            firstName = data.firstName.trim(),
            lastName = data.lastName.trim(),
            email = data.email.trim().lowercase(),
            phone = Validators.normalizeChileanPhone(data.phone) ?: data.phone,
            city = data.city.trim(),
            commune = data.commune.trim(),
        )
        return authRepository.register(normalized)
    }
}

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> {
        if (!Validators.isValidEmail(email)) return AppResult.Failure(AppError.Validation("EMAIL"))
        if (password.isEmpty()) return AppResult.Failure(AppError.Validation("PASSWORD"))
        return authRepository.signIn(email.trim().lowercase(), password)
    }
}
