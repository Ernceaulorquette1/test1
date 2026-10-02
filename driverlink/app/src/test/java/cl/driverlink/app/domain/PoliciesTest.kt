package cl.driverlink.app.domain

import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.CompanyRole
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.PlatformRole
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.policy.AlertExpirationPolicy
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.policy.PlatformPermission
import cl.driverlink.app.domain.policy.RolePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAccessPolicyTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test fun `plan gratuito no tiene SOS`() {
        assertFalse(FeatureAccessPolicy.hasAccess(Subscription.FREE, PremiumFeature.SOS, now))
    }

    @Test fun `trial vigente da acceso premium`() {
        val trial = Subscription(SubscriptionStatus.TRIAL, now - day, now + 10 * day, trialUsed = true, source = "trial")
        assertTrue(FeatureAccessPolicy.hasAccess(trial, PremiumFeature.SOS, now))
        assertEquals(10, FeatureAccessPolicy.remainingDays(trial, now))
    }

    @Test fun `trial vencido pasa a EXPIRED y bloquea solo premium`() {
        val trial = Subscription(SubscriptionStatus.TRIAL, now - 31 * day, now - 1, trialUsed = true, source = "trial")
        assertEquals(SubscriptionStatus.EXPIRED, FeatureAccessPolicy.effectiveStatus(trial, now))
        assertFalse(FeatureAccessPolicy.hasAccess(trial, PremiumFeature.EMERGENCY_CONTACTS, now))
        assertFalse(FeatureAccessPolicy.canStartTrial(trial, now))
    }

    @Test fun `trial solo una vez`() {
        assertTrue(FeatureAccessPolicy.canStartTrial(Subscription.FREE, now))
        assertFalse(FeatureAccessPolicy.canStartTrial(Subscription.FREE.copy(trialUsed = true), now))
    }

    @Test fun `plan empresa incluye premium`() {
        val company = Subscription(SubscriptionStatus.COMPANY, now, null, trialUsed = false, source = "company")
        assertTrue(FeatureAccessPolicy.hasPremium(company, now))
    }
}

class RolePolicyTest {
    private fun member(role: CompanyRole, status: MemberStatus = MemberStatus.ACTIVE) =
        CompanyMember("c1", "u1", "Test", role, status, 0, 0)

    @Test fun `conductor no puede moderar ni administrar`() {
        assertTrue(RolePolicy.has(PlatformRole.DRIVER, PlatformPermission.USE_COMMUNITY))
        assertFalse(RolePolicy.has(PlatformRole.DRIVER, PlatformPermission.MODERATE_CONTENT))
        assertFalse(RolePolicy.has(PlatformRole.DRIVER, PlatformPermission.MANAGE_SOS))
    }

    @Test fun `operador SOS no modera contenido`() {
        assertTrue(RolePolicy.has(PlatformRole.SOS_OPERATOR, PlatformPermission.MANAGE_SOS))
        assertFalse(RolePolicy.has(PlatformRole.SOS_OPERATOR, PlatformPermission.MODERATE_CONTENT))
    }

    @Test fun `miembro pendiente no tiene permisos`() {
        assertFalse(RolePolicy.has(member(CompanyRole.OWNER, MemberStatus.PENDING), CompanyPermission.USE_COMPANY_CHANNELS))
        assertFalse(RolePolicy.has(null, CompanyPermission.USE_COMPANY_CHANNELS))
    }

    @Test fun `roles de empresa`() {
        assertTrue(RolePolicy.has(member(CompanyRole.DRIVER), CompanyPermission.START_WORK_SESSION))
        assertFalse(RolePolicy.has(member(CompanyRole.DRIVER), CompanyPermission.APPROVE_MEMBERS))
        assertTrue(RolePolicy.has(member(CompanyRole.ADMIN), CompanyPermission.APPROVE_MEMBERS))
        assertFalse(RolePolicy.has(member(CompanyRole.ADMIN), CompanyPermission.MANAGE_COMPANY))
        assertTrue(RolePolicy.has(member(CompanyRole.OWNER), CompanyPermission.MANAGE_COMPANY))
        assertTrue(RolePolicy.has(member(CompanyRole.DISPATCHER), CompanyPermission.VIEW_FLEET_MAP))
        assertFalse(RolePolicy.has(member(CompanyRole.DISPATCHER), CompanyPermission.MANAGE_VEHICLES))
    }
}

class AlertExpirationPolicyTest {
    @Test fun `usa minutos configurados por categoria`() {
        val policy = AlertExpirationPolicy(mapOf(AlertCategory.TRAFFIC to 30))
        assertEquals(1_000L + 30 * 60_000L, policy.expiresAt(AlertCategory.TRAFFIC, 1_000L))
    }

    @Test fun `usa valor por defecto si falta la categoria`() {
        val policy = AlertExpirationPolicy(emptyMap())
        assertEquals(360 * 60_000L, policy.expiresAt(AlertCategory.ROAD_CLOSED, 0L))
    }

    @Test fun `limita valores fuera de rango`() {
        val policy = AlertExpirationPolicy(mapOf(AlertCategory.OTHER to 100_000, AlertCategory.DANGER to 0))
        assertEquals(AlertExpirationPolicy.MAX_MINUTES * 60_000L, policy.expiresAt(AlertCategory.OTHER, 0L))
        assertEquals(AlertExpirationPolicy.MIN_MINUTES * 60_000L, policy.expiresAt(AlertCategory.DANGER, 0L))
    }
}
