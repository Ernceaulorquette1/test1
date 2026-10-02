package cl.driverlink.app.data.demo

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.BlockedUser
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.NotificationPreferences
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.ProOffer
import cl.driverlink.app.domain.model.ReportDraft
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.repository.BillingGateway
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.EmergencyContactRepository
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.domain.repository.NotificationPreferencesRepository
import cl.driverlink.app.domain.repository.SosOperatorRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import cl.driverlink.app.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DemoSubscriptionRepository(private val b: DemoBackend) : SubscriptionRepository {

    override fun observeSubscription(): Flow<Subscription> =
        combine(b.currentUserId, b.subscriptions) { uid, subs -> uid?.let { subs[it] } ?: Subscription.FREE }

    /** Mecanismo de desarrollo equivalente a la Cloud Function `startTrial`: una sola vez por cuenta. */
    override suspend fun startTrial(): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        val now = b.clock.now()
        val current = b.subscriptions.value[uid] ?: Subscription.FREE
        if (!FeatureAccessPolicy.canStartTrial(current, now)) return AppResult.Failure(AppError.AlreadyExists)
        val days = b.config.value.trialDays
        b.subscriptions.update {
            it + (uid to Subscription(SubscriptionStatus.TRIAL, now, now + days * 24L * 60 * 60 * 1000, trialUsed = true, source = "trial"))
        }
        return AppResult.Success(Unit)
    }
}

class DemoBillingGateway(private val b: DemoBackend) : BillingGateway {
    override suspend fun loadProOffer(): AppResult<ProOffer> =
        AppResult.Success(ProOffer(b.config.value.premiumPriceDisplay, b.config.value.trialDays, billingAvailable = false))

    /** La demo no cobra: requiere Google Play Billing configurado (ver PROJECT_STATUS.md). */
    override suspend fun purchasePro(): AppResult<Unit> = AppResult.Failure(AppError.NotConfigured)
}

class DemoSosRepository(private val b: DemoBackend) : SosRepository, SosOperatorRepository {

    override suspend fun createSos(type: SosType, location: GeoPoint?, notes: String): AppResult<String> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        // Validación de servidor: SOS es Premium.
        val sub = b.subscriptions.value[user.id] ?: Subscription.FREE
        if (!FeatureAccessPolicy.hasAccess(sub, PremiumFeature.SOS, b.clock.now())) return AppResult.Failure(AppError.PremiumRequired)
        b.latency()
        val now = b.clock.now()
        val id = "sos-" + b.newId()
        b.sosEvents.update {
            it + (id to SosEvent(id, user.id, user.displayName, user.phone, type, location, SosStatus.CREATED, notes, null, now, now, null))
        }
        b.simulateOperator(id)
        return AppResult.Success(id)
    }

    override fun observeSos(sosId: String): Flow<SosEvent?> =
        combine(b.sosEvents, b.currentUserId) { map, uid -> map[sosId]?.takeIf { it.userId == uid } }

    override fun observeMyActiveSos(): Flow<SosEvent?> = combine(b.sosEvents, b.currentUserId) { map, uid ->
        map.values.filter { it.userId == uid && it.status.isActive }.maxByOrNull { it.createdAt }
    }

    override suspend fun cancelSos(sosId: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val event = b.sosEvents.value[sosId] ?: return AppResult.Failure(AppError.NotFound)
        if (event.userId != uid) return AppResult.Failure(AppError.PermissionDenied)
        if (!event.status.isActive) return AppResult.Success(Unit)
        b.latency()
        val now = b.clock.now()
        b.sosEvents.update { it + (sosId to event.copy(status = SosStatus.CANCELLED, updatedAt = now, resolvedAt = now)) }
        return AppResult.Success(Unit)
    }

    override suspend fun pushLocation(sosId: String, point: GeoPoint): AppResult<Unit> {
        b.sosEvents.update { map ->
            val e = map[sosId] ?: return@update map
            if (!e.status.isActive) map else map + (sosId to e.copy(location = point, updatedAt = b.clock.now()))
        }
        return AppResult.Success(Unit)
    }

    // Operaciones de operador (panel futuro).
    override fun observeActiveCases(): Flow<List<SosEvent>> = b.sosEvents.map { m -> m.values.filter { it.status.isActive } }
    override suspend fun acceptCase(sosId: String): AppResult<Unit> = updateStatus(sosId, SosStatus.RECEIVED)
    override suspend fun updateStatus(sosId: String, status: SosStatus): AppResult<Unit> {
        b.sosEvents.update { m -> m[sosId]?.let { m + (sosId to it.copy(status = status, updatedAt = b.clock.now())) } ?: m }
        return AppResult.Success(Unit)
    }
    override suspend fun addNote(sosId: String, note: String): AppResult<Unit> {
        b.sosEvents.update { m -> m[sosId]?.let { m + (sosId to it.copy(notes = (it.notes + "\n" + note).trim())) } ?: m }
        return AppResult.Success(Unit)
    }
}

class DemoEmergencyContactRepository(private val b: DemoBackend) : EmergencyContactRepository {
    override fun observeContacts(): Flow<List<EmergencyContact>> =
        combine(b.currentUserId, b.contacts) { uid, map -> uid?.let { map[it] } ?: emptyList() }

    override suspend fun saveContact(contact: EmergencyContact): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val sub = b.subscriptions.value[uid] ?: Subscription.FREE
        if (!FeatureAccessPolicy.hasAccess(sub, PremiumFeature.EMERGENCY_CONTACTS, b.clock.now())) {
            return AppResult.Failure(AppError.PremiumRequired)
        }
        b.contacts.update { it + (uid to ((it[uid] ?: emptyList()).filterNot { c -> c.id == contact.id } + contact)) }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteContact(contactId: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.contacts.update { it + (uid to (it[uid] ?: emptyList()).filterNot { c -> c.id == contactId }) }
        return AppResult.Success(Unit)
    }
}

class DemoModerationRepository(private val b: DemoBackend) : ModerationRepository {
    override suspend fun report(draft: ReportDraft): AppResult<Unit> {
        b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        b.reports.update { it + draft }
        return AppResult.Success(Unit)
    }

    override suspend fun blockUser(userId: String, displayName: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        if (uid == userId) return AppResult.Failure(AppError.Validation("SELF"))
        b.blocked.update {
            val list = (it[uid] ?: emptyList()).filterNot { u -> u.userId == userId }
            it + (uid to list + BlockedUser(userId, displayName, b.clock.now()))
        }
        return AppResult.Success(Unit)
    }

    override suspend fun unblockUser(userId: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.blocked.update { it + (uid to (it[uid] ?: emptyList()).filterNot { u -> u.userId == userId }) }
        return AppResult.Success(Unit)
    }

    override fun observeBlockedUsers(): Flow<List<BlockedUser>> =
        combine(b.currentUserId, b.blocked) { uid, map -> uid?.let { map[it] } ?: emptyList() }
}

class DemoNotificationPreferencesRepository(private val b: DemoBackend) : NotificationPreferencesRepository {
    override fun observePreferences(): Flow<NotificationPreferences> =
        combine(b.currentUserId, b.notificationPrefs) { uid, map -> uid?.let { map[it] } ?: NotificationPreferences.DEFAULT }

    override suspend fun updatePreferences(preferences: NotificationPreferences): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.notificationPrefs.update { it + (uid to preferences) }
        return AppResult.Success(Unit)
    }
}

class DemoConfigRepository(private val b: DemoBackend) : ConfigRepository {
    override val config: StateFlow<AppConfig> = b.config
    override suspend fun refresh() = Unit
}
