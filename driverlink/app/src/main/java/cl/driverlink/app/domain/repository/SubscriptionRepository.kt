package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.ProOffer
import cl.driverlink.app.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    /** Estado emitido por el servidor. Nunca se calcula ni se guarda solo en el teléfono. */
    fun observeSubscription(): Flow<Subscription>
    /** Solicita al servidor activar la prueba (una sola vez por cuenta). */
    suspend fun startTrial(): AppResult<Unit>
}

/**
 * Pasarela de facturación de la tienda (Google Play Billing). La compra se valida
 * siempre en servidor antes de otorgar acceso.
 */
interface BillingGateway {
    suspend fun loadProOffer(): AppResult<ProOffer>
    /** Inicia la compra. Devuelve NotConfigured mientras no exista producto en Play Console. */
    suspend fun purchasePro(): AppResult<Unit>
}
