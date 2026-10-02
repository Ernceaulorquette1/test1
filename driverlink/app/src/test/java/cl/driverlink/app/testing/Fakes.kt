package cl.driverlink.app.testing

import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.SosRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FixedClock(var time: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = time
}

class FakeConfigRepository(initial: AppConfig = AppConfig()) : ConfigRepository {
    override val config = MutableStateFlow(initial)
    override suspend fun refresh() = Unit
}

class FakeAnalytics : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent, params: Map<String, String>) { events += event }
    override fun setUserId(userId: String?) = Unit
}

class FakeAuthRepository : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var signInResult: AppResult<Unit> = AppResult.Success(Unit)
    var lastRegistration: RegistrationData? = null
    var signInCalls = 0

    override val authState: Flow<AuthState> = state
    override fun currentUserId(): String? = (state.value as? AuthState.SignedIn)?.userId
    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        signInCalls++
        return signInResult
    }
    override suspend fun register(data: RegistrationData): AppResult<Unit> {
        lastRegistration = data
        return AppResult.Success(Unit)
    }
    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun signOut() { state.value = AuthState.SignedOut }
    override suspend fun deleteAccount(): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeAlertRepository : AlertRepository {
    val votes = mutableMapOf<String, AlertVote>()
    var created: Pair<AlertDraft, Long>? = null

    override fun observeActiveAlerts(city: String?): Flow<List<Alert>> = flowOf(emptyList())
    override fun observeAlert(alertId: String): Flow<Alert?> = flowOf(null)
    override suspend fun createAlert(draft: AlertDraft, expiresAt: Long): AppResult<String> {
        created = draft to expiresAt
        return AppResult.Success("alert-1")
    }
    override suspend fun vote(alertId: String, vote: AlertVote): AppResult<Unit> {
        if (votes.containsKey(alertId)) return AppResult.Failure(AppError.AlreadyVoted)
        votes[alertId] = vote
        return AppResult.Success(Unit)
    }
    override suspend fun myVote(alertId: String): AppResult<AlertVote?> = AppResult.Success(votes[alertId])
}

class FakeSubscriptionRepository(initial: Subscription = Subscription.FREE) : SubscriptionRepository {
    val subscription = MutableStateFlow(initial)
    override fun observeSubscription(): Flow<Subscription> = subscription
    override suspend fun startTrial(): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeSosRepository : SosRepository {
    val active = MutableStateFlow<SosEvent?>(null)
    var createCalls = 0
    override suspend fun createSos(type: SosType, location: GeoPoint?, notes: String): AppResult<String> {
        createCalls++
        return AppResult.Success("sos-$createCalls")
    }
    override fun observeSos(sosId: String): Flow<SosEvent?> = active
    override fun observeMyActiveSos(): Flow<SosEvent?> = active
    override suspend fun cancelSos(sosId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun pushLocation(sosId: String, point: GeoPoint): AppResult<Unit> = AppResult.Success(Unit)
}

