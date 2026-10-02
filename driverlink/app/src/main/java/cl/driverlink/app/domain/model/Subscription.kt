package cl.driverlink.app.domain.model

enum class SubscriptionStatus { FREE, TRIAL, PRO, EXPIRED, COMPANY }

/**
 * Estado de suscripción tal como lo publica el servidor (colección `subscriptions`).
 * El cliente nunca escribe este documento.
 */
data class Subscription(
    val status: SubscriptionStatus,
    val startedAt: Long?,
    val expiresAt: Long?,
    val trialUsed: Boolean,
    val source: String?,
) {
    companion object {
        val FREE = Subscription(SubscriptionStatus.FREE, null, null, trialUsed = false, source = null)
    }
}

/** Funciones Premium. Las funciones de comunidad no aparecen aquí: siempre son gratuitas. */
enum class PremiumFeature { SOS, EMERGENCY_CONTACTS, ADVANCED_ASSISTANCE }

/** Oferta comercial de DriverLink Pro. El precio viene de configuración o de la tienda. */
data class ProOffer(
    val priceDisplay: String,
    val trialDays: Int,
    val billingAvailable: Boolean,
)
