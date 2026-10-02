package cl.driverlink.app.domain.usecase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.CompanyDraft
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.domain.repository.VehicleRepository
import cl.driverlink.app.domain.validation.RutValidator
import cl.driverlink.app.domain.validation.Validators

enum class CompanyField { RUT, LEGAL_NAME, TRADE_NAME, EMAIL, PHONE, ADDRESS }

object CompanyValidator {
    fun validate(draft: CompanyDraft): Set<CompanyField> = buildSet {
        if (!RutValidator.isValid(draft.rut)) add(CompanyField.RUT)
        if (draft.legalName.isBlank()) add(CompanyField.LEGAL_NAME)
        if (draft.tradeName.isBlank()) add(CompanyField.TRADE_NAME)
        if (!Validators.isValidEmail(draft.email)) add(CompanyField.EMAIL)
        if (!Validators.isValidPhone(draft.phone)) add(CompanyField.PHONE)
        if (draft.address.isBlank()) add(CompanyField.ADDRESS)
    }
}

class CreateCompanyUseCase(private val companyRepository: CompanyRepository) {
    suspend operator fun invoke(draft: CompanyDraft): AppResult<String> {
        val errors = CompanyValidator.validate(draft)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors.first().name))
        return companyRepository.createCompany(
            draft.copy(
                rut = RutValidator.normalize(draft.rut) ?: draft.rut,
                phone = Validators.normalizeChileanPhone(draft.phone) ?: draft.phone,
                email = draft.email.trim().lowercase(),
            )
        )
    }
}

class JoinCompanyUseCase(private val companyRepository: CompanyRepository) {
    suspend operator fun invoke(code: String): AppResult<Unit> {
        val normalized = code.trim().uppercase()
        if (!Validators.isValidInviteCode(normalized)) return AppResult.Failure(AppError.Validation("CODE"))
        return companyRepository.requestJoin(normalized)
    }
}

class CreateVehicleUseCase(private val vehicleRepository: VehicleRepository) {
    suspend operator fun invoke(companyId: String, draft: VehicleDraft, currentYear: Int): AppResult<String> {
        val plate = Validators.normalizePlate(draft.plate)
        if (!Validators.isValidPlate(plate)) return AppResult.Failure(AppError.Validation("PLATE"))
        if (draft.brand.isBlank()) return AppResult.Failure(AppError.Validation("BRAND"))
        if (draft.model.isBlank()) return AppResult.Failure(AppError.Validation("MODEL"))
        if (!Validators.isValidVehicleYear(draft.year, currentYear)) return AppResult.Failure(AppError.Validation("YEAR"))
        return vehicleRepository.createVehicle(companyId, draft.copy(plate = plate, brand = draft.brand.trim(), model = draft.model.trim()))
    }
}
