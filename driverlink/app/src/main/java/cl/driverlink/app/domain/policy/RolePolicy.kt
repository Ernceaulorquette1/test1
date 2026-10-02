package cl.driverlink.app.domain.policy

import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.CompanyRole
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.PlatformRole

/** Permisos de plataforma. Las áreas (comunidad, SOS, administración) no se mezclan. */
enum class PlatformPermission {
    USE_COMMUNITY,
    MODERATE_CONTENT,
    REVIEW_VERIFICATIONS,
    SUSPEND_USERS,
    MANAGE_CHANNELS,
    MANAGE_CONFIG,
    MANAGE_SOS,
    VIEW_METRICS,
}

/** Permisos dentro de una empresa. Solo aplican a miembros ACTIVE de esa empresa. */
enum class CompanyPermission {
    USE_COMPANY_CHANNELS,
    START_WORK_SESSION,
    VIEW_MEMBERS,
    APPROVE_MEMBERS,
    MANAGE_VEHICLES,
    ASSIGN_VEHICLES,
    MANAGE_CHANNELS,
    VIEW_FLEET_MAP,
    MANAGE_COMPANY,
}

object RolePolicy {

    private val platform: Map<PlatformRole, Set<PlatformPermission>> = mapOf(
        PlatformRole.DRIVER to setOf(PlatformPermission.USE_COMMUNITY),
        PlatformRole.MODERATOR to setOf(
            PlatformPermission.USE_COMMUNITY,
            PlatformPermission.MODERATE_CONTENT,
            PlatformPermission.REVIEW_VERIFICATIONS,
        ),
        PlatformRole.SOS_OPERATOR to setOf(
            PlatformPermission.USE_COMMUNITY,
            PlatformPermission.MANAGE_SOS,
        ),
        PlatformRole.ADMIN to PlatformPermission.entries.toSet(),
    )

    private val company: Map<CompanyRole, Set<CompanyPermission>> = mapOf(
        CompanyRole.DRIVER to setOf(
            CompanyPermission.USE_COMPANY_CHANNELS,
            CompanyPermission.START_WORK_SESSION,
        ),
        CompanyRole.DISPATCHER to setOf(
            CompanyPermission.USE_COMPANY_CHANNELS,
            CompanyPermission.VIEW_MEMBERS,
            CompanyPermission.VIEW_FLEET_MAP,
        ),
        CompanyRole.SUPERVISOR to setOf(
            CompanyPermission.USE_COMPANY_CHANNELS,
            CompanyPermission.VIEW_MEMBERS,
            CompanyPermission.VIEW_FLEET_MAP,
            CompanyPermission.ASSIGN_VEHICLES,
            CompanyPermission.START_WORK_SESSION,
        ),
        CompanyRole.ADMIN to CompanyPermission.entries.toSet() - CompanyPermission.MANAGE_COMPANY,
        CompanyRole.OWNER to CompanyPermission.entries.toSet(),
    )

    fun has(role: PlatformRole, permission: PlatformPermission): Boolean =
        permission in platform.getValue(role)

    fun has(member: CompanyMember?, permission: CompanyPermission): Boolean {
        if (member == null || member.status != MemberStatus.ACTIVE) return false
        return permission in company.getValue(member.role)
    }
}
