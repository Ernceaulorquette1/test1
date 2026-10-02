package cl.driverlink.app.data.firebase

import cl.driverlink.app.R
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.BlockedUser
import cl.driverlink.app.domain.model.NotificationCategory
import cl.driverlink.app.domain.model.NotificationPreferences
import cl.driverlink.app.domain.model.OfficialService
import cl.driverlink.app.domain.model.ProOffer
import cl.driverlink.app.domain.model.ReportDraft
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.BillingGateway
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.domain.repository.NotificationPreferencesRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseSubscriptionRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : SubscriptionRepository {

    /** `subscriptions/{uid}` es de solo lectura para el cliente: lo escriben las Cloud Functions. */
    override fun observeSubscription(): Flow<Subscription> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(Subscription.FREE)
        else db.collection(Paths.SUBSCRIPTIONS).document(state.userId).observe(crash, Subscription.FREE) { doc ->
            if (!doc.exists()) Subscription.FREE
            else Subscription(
                status = doc.enum("status", SubscriptionStatus.FREE),
                startedAt = doc.millis("startedAt"),
                expiresAt = doc.millis("expiresAt"),
                trialUsed = doc.getBoolean("trialUsed") ?: false,
                source = doc.getString("source"),
            )
        }
    }

    override suspend fun startTrial(): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        functions.getHttpsCallable("startTrial").call().await()
        Unit
    }
}

/**
 * Pasarela de facturación. Google Play Billing requiere publicar la app y crear el producto
 * `driverlink_pro_monthly` en Play Console; hasta entonces la compra devuelve NotConfigured.
 * La validación de compras ya existe en servidor: Cloud Function `verifyPlayPurchase`.
 */
class ConfigBillingGateway(private val config: ConfigRepository) : BillingGateway {
    override suspend fun loadProOffer(): AppResult<ProOffer> {
        val c = config.config.value
        return AppResult.Success(ProOffer(c.premiumPriceDisplay, c.trialDays, billingAvailable = false))
    }

    override suspend fun purchasePro(): AppResult<Unit> = AppResult.Failure(AppError.NotConfigured)
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseModerationRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : ModerationRepository {

    override suspend fun report(draft: ReportDraft): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        db.collection(Paths.REPORTS).add(
            mapOf(
                "reporterId" to uid,
                "targetType" to draft.targetType.name,
                "targetId" to draft.targetId,
                "targetOwnerId" to draft.targetOwnerId,
                "reason" to draft.reason.name,
                "details" to draft.details,
                "contextPath" to draft.contextPath,
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }

    override suspend fun blockUser(userId: String, displayName: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        if (uid == userId) throw AppException(AppError.Validation("SELF"))
        db.collection(Paths.USERS).document(uid).collection(Paths.BLOCKED_USERS).document(userId)
            .set(mapOf("displayName" to displayName, "blockedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun unblockUser(userId: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        db.collection(Paths.USERS).document(uid).collection(Paths.BLOCKED_USERS).document(userId).delete().await()
        Unit
    }

    override fun observeBlockedUsers(): Flow<List<BlockedUser>> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(emptyList())
        else db.collection(Paths.USERS).document(state.userId).collection(Paths.BLOCKED_USERS)
            .orderBy("blockedAt", Query.Direction.DESCENDING)
            .observe(crash, emptyList()) { snap ->
                snap.documents.map { BlockedUser(it.id, it.str("displayName"), it.millis("blockedAt") ?: 0L) }
            }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseNotificationPreferencesRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val crash: CrashReporter,
) : NotificationPreferencesRepository {

    override fun observePreferences(): Flow<NotificationPreferences> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(NotificationPreferences.DEFAULT)
        else db.collection(Paths.NOTIFICATION_PREFERENCES).document(state.userId)
            .observe(crash, NotificationPreferences.DEFAULT) { doc ->
                val stored = (doc.get("categories") as? Map<*, *>).orEmpty()
                NotificationPreferences(
                    NotificationCategory.entries.associateWith { (stored[it.name] as? Boolean) ?: it.defaultEnabled }
                )
            }
    }

    override suspend fun updatePreferences(preferences: NotificationPreferences): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        db.collection(Paths.NOTIFICATION_PREFERENCES).document(uid).set(
            mapOf(
                "categories" to NotificationCategory.entries.associate { it.name to preferences.isEnabled(it) },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }
}

/** Configuración remota con valores por defecto empaquetados (res/xml/remote_config_defaults.xml). */
class FirebaseConfigRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val crash: CrashReporter,
) : ConfigRepository {

    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3_600).build()
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
    }

    override suspend fun refresh() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            crash.recordException(e)
        }
        _config.value = parse()
    }

    private fun parse(): AppConfig {
        val defaults = AppConfig()
        return AppConfig(
            trialDays = remoteConfig.getLong("trial_days").toInt().takeIf { it > 0 } ?: defaults.trialDays,
            maxAudioDurationSeconds = remoteConfig.getLong("max_audio_duration_seconds").toInt().coerceIn(5, 120),
            alertExpirationMinutes = parseExpiration(remoteConfig.getString("alert_expiration_minutes")) ?: defaults.alertExpirationMinutes,
            premiumPriceDisplay = remoteConfig.getString("premium_price_display").ifBlank { defaults.premiumPriceDisplay },
            sosEnabled = remoteConfig.getBoolean("sos_enabled"),
            companyFeaturesEnabled = remoteConfig.getBoolean("company_features_enabled"),
            maintenanceMode = remoteConfig.getBoolean("maintenance_mode"),
            sosHoldMillis = remoteConfig.getLong("sos_hold_millis").takeIf { it in 1_000..10_000 } ?: defaults.sosHoldMillis,
            chatPageSize = remoteConfig.getLong("chat_page_size").toInt().takeIf { it in 10..100 } ?: defaults.chatPageSize,
            alertResolveThreshold = remoteConfig.getLong("alert_resolve_threshold").toInt().takeIf { it > 0 } ?: defaults.alertResolveThreshold,
            sosLocationIntervalSeconds = remoteConfig.getLong("sos_location_interval_seconds").toInt().takeIf { it >= 10 } ?: defaults.sosLocationIntervalSeconds,
            workLocationIntervalSeconds = remoteConfig.getLong("work_location_interval_seconds").toInt().takeIf { it >= 30 } ?: defaults.workLocationIntervalSeconds,
            officialServices = parseServices(remoteConfig.getString("official_services")) ?: defaults.officialServices,
        )
    }

    private fun parseExpiration(json: String): Map<AlertCategory, Int>? = try {
        val obj = JSONObject(json)
        AlertCategory.entries.associateWith { obj.optInt(it.name, AppConfig.DEFAULT_ALERT_EXPIRATION.getValue(it)) }
    } catch (e: Exception) {
        null
    }

    private fun parseServices(json: String): List<OfficialService>? = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            OfficialService(o.getString("name"), o.getString("number"))
        }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
