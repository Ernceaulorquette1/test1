package cl.driverlink.app.domain.policy

import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus

/**
 * Decide el acceso a funciones Premium a partir del estado emitido por el servidor.
 * Las funciones de comunidad (chat, radio, mapa, alertas) no pasan por aquí: son siempre gratuitas.
 *
 * Esta verificación es de experiencia de usuario; la autorización real la aplican
 * las reglas de Firestore y las Cloud Functions.
 */
object FeatureAccessPolicy {

    fun effectiveStatus(subscription: Subscription, now: Long): SubscriptionStatus {
        val expiresAt = subscription.expiresAt
        return when (subscription.status) {
            SubscriptionStatus.TRIAL, SubscriptionStatus.PRO, SubscriptionStatus.COMPANY ->
                if (expiresAt != null && expiresAt <= now) SubscriptionStatus.EXPIRED else subscription.status
            else -> subscription.status
        }
    }

    fun hasPremium(subscription: Subscription, now: Long): Boolean =
        effectiveStatus(subscription, now) in PREMIUM_STATUSES

    fun hasAccess(subscription: Subscription, feature: PremiumFeature, now: Long): Boolean =
        when (feature) {
            PremiumFeature.SOS,
            PremiumFeature.EMERGENCY_CONTACTS,
            PremiumFeature.ADVANCED_ASSISTANCE -> hasPremium(subscription, now)
        }

    fun canStartTrial(subscription: Subscription, now: Long): Boolean =
        !subscription.trialUsed && !hasPremium(subscription, now)

    /** Días restantes (redondeo hacia arriba) o null si no hay vencimiento. */
    fun remainingDays(subscription: Subscription, now: Long): Int? {
        val expiresAt = subscription.expiresAt ?: return null
        if (expiresAt <= now) return 0
        val day = 24L * 60 * 60 * 1000
        return ((expiresAt - now + day - 1) / day).toInt()
    }

    private val PREMIUM_STATUSES = setOf(
        SubscriptionStatus.TRIAL,
        SubscriptionStatus.PRO,
        SubscriptionStatus.COMPANY,
    )
}
