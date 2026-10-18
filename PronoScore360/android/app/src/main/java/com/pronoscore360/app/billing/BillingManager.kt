package com.pronoscore360.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.pronoscore360.core.api.apiCall
import com.pronoscore360.core.model.PurchaseRequest
import com.pronoscore360.core.model.RestoreRequest
import com.pronoscore360.core.model.User
import com.pronoscore360.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

const val PRODUCT_PRO_MONTHLY = "pronoscore360_pro_monthly"

sealed interface BillingEvent {
    data class Verified(val user: User) : BillingEvent
    data class Failed(val message: String) : BillingEvent
    data object Pending : BillingEvent
    data object Canceled : BillingEvent
}

/**
 * Google Play Billing. REGLA: el teléfono NUNCA activa Pro por sí mismo. Tras una compra se envía el
 * purchaseToken al backend, que lo verifica con Google Play Developer API y devuelve el plan efectivo.
 * (Sin compilar/probar en este entorno: requiere Play Console + cuenta de pruebas. Ver CONFIGURACION_GOOGLE_PLAY.md.)
 */
class BillingManager(context: Context, private val repo: Repository) {
    val events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    private val _details = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _details

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> handleUpdate(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()
    private var scope: CoroutineScope? = null
    fun attach(scope: CoroutineScope) { this.scope = scope }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) { if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK) }
                override fun onBillingServiceDisconnected() { /* se reconecta en la próxima operación */ }
            })
        }
    }

    suspend fun loadProduct(): ProductDetails? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_PRO_MONTHLY).setProductType(BillingClient.ProductType.SUBS).build())).build()
        val res = client.queryProductDetails(params)
        return res.productDetailsList?.firstOrNull().also { _details.value = it }
    }

    /** Muestra la hoja de compra de Google Play. El id ofuscado (del backend) vincula la compra con esta cuenta. */
    suspend fun launch(activity: Activity) {
        val pd = _details.value ?: loadProduct() ?: run { events.tryEmit(BillingEvent.Failed("Google Play no está disponible o el producto no existe.")); return }
        val offer = pd.subscriptionOfferDetails?.firstOrNull() ?: run { events.tryEmit(BillingEvent.Failed("No hay oferta de suscripción disponible.")); return }
        val accountId = try { apiCall { repo.api.accountId() }.obfuscatedAccountId } catch (e: Exception) { events.tryEmit(BillingEvent.Failed(e.message ?: "Error")); return }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(offer.offerToken).build()))
            .setObfuscatedAccountId(accountId).build()
        val r = client.launchBillingFlow(activity, flow)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) events.tryEmit(BillingEvent.Failed("No se pudo iniciar la compra (${r.debugMessage})"))
    }

    private fun handleUpdate(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { list -> scope?.launch { verify(list) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> events.tryEmit(BillingEvent.Canceled)
            else -> events.tryEmit(BillingEvent.Failed("Compra no completada (${result.debugMessage})"))
        }
    }

    private suspend fun verify(list: List<Purchase>) {
        for (p in list) {
            when (p.purchaseState) {
                Purchase.PurchaseState.PENDING -> events.tryEmit(BillingEvent.Pending)     // pago pendiente: sin acceso Pro todavía
                Purchase.PurchaseState.PURCHASED -> try {
                    val r = apiCall { repo.api.verifyPurchase(PurchaseRequest(p.purchaseToken)) }   // el backend verifica y confirma (acknowledge)
                    events.tryEmit(BillingEvent.Verified(r.effective))
                } catch (e: Exception) { events.tryEmit(BillingEvent.Failed(e.message ?: "No se pudo verificar la compra")) }
                else -> Unit
            }
        }
    }

    /** Restaurar compras: re-verifica en el servidor las suscripciones que Google Play reporta para la cuenta. */
    suspend fun restore() {
        if (!connect()) { events.tryEmit(BillingEvent.Failed("Google Play no está disponible.")); return }
        val res = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        val tokens = res.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.map { it.purchaseToken }
        if (tokens.isEmpty()) { events.tryEmit(BillingEvent.Failed("No se encontraron compras para restaurar.")); return }
        try { events.tryEmit(BillingEvent.Verified(apiCall { repo.api.restorePurchases(RestoreRequest(tokens)) }.effective)) }
        catch (e: Exception) { events.tryEmit(BillingEvent.Failed(e.message ?: "Error al restaurar")) }
    }
}
