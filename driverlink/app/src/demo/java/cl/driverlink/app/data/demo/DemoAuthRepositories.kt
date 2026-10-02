package cl.driverlink.app.data.demo

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.AccountStatus
import cl.driverlink.app.domain.model.PlatformRole
import cl.driverlink.app.domain.model.ProfileUpdate
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.model.Reputation
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.VerificationRequest
import cl.driverlink.app.domain.model.VerificationStatus
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DemoAuthRepository(private val b: DemoBackend) : AuthRepository {

    override val authState: Flow<AuthState> =
        b.currentUserId.map { uid -> if (uid == null) AuthState.SignedOut else AuthState.SignedIn(uid) }

    override fun currentUserId(): String? = b.currentUserId.value

    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        b.latency()
        val (expected, uid) = b.passwords[email.lowercase()] ?: return AppResult.Failure(AppError.InvalidCredentials)
        if (expected != password) return AppResult.Failure(AppError.InvalidCredentials)
        if (b.users.value[uid]?.accountStatus == AccountStatus.SUSPENDED) return AppResult.Failure(AppError.PermissionDenied)
        b.currentUserId.value = uid
        return AppResult.Success(Unit)
    }

    override suspend fun register(data: RegistrationData): AppResult<Unit> {
        b.latency()
        if (b.passwords.containsKey(data.email)) return AppResult.Failure(AppError.EmailInUse)
        val uid = "u-" + b.newId()
        val now = b.clock.now()
        // Igual que en producción: el perfil nace sin verificar, plan gratuito y rol conductor.
        val user = User(
            id = uid, firstName = data.firstName, lastName = data.lastName, email = data.email, phone = data.phone,
            photoUrl = data.photoUri, city = data.city, commune = data.commune, platforms = data.platforms.toList(),
            verificationStatus = VerificationStatus.UNVERIFIED, accountStatus = AccountStatus.ACTIVE,
            subscriptionStatus = SubscriptionStatus.FREE, role = PlatformRole.DRIVER, reputation = Reputation(),
            createdAt = now, updatedAt = now,
        )
        b.passwords[data.email] = data.password to uid
        b.users.update { it + (uid to user) }
        b.subscriptions.update { it + (uid to Subscription.FREE) }
        b.currentUserId.value = uid
        return AppResult.Success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> {
        b.latency()
        return AppResult.Success(Unit)
    }

    override suspend fun signOut() {
        b.currentUserId.value = null
    }

    override suspend fun deleteAccount(): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        b.passwords.entries.removeAll { it.value.second == uid }
        b.users.update { it - uid }
        b.currentUserId.value = null
        return AppResult.Success(Unit)
    }
}

class DemoUserRepository(private val b: DemoBackend) : UserRepository {

    override fun observeCurrentUser(): Flow<User?> =
        combine(b.currentUserId, b.users, b.subscriptions) { uid, users, subs ->
            val user = uid?.let { users[it] } ?: return@combine null
            user.copy(subscriptionStatus = subs[user.id]?.status ?: SubscriptionStatus.FREE)
        }

    override suspend fun updateProfile(update: ProfileUpdate): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        b.users.update { users ->
            val user = users[uid] ?: return@update users
            // Solo campos autorizados: el estado de verificación, rol y plan no se tocan.
            users + (uid to user.copy(
                firstName = update.firstName, lastName = update.lastName, phone = update.phone,
                city = update.city, commune = update.commune, platforms = update.platforms.toList(),
                photoUrl = update.newPhotoUri ?: user.photoUrl, updatedAt = b.clock.now(),
            ))
        }
        return AppResult.Success(Unit)
    }

    override fun observeVerification(): Flow<VerificationRequest?> =
        combine(b.currentUserId, b.verifications) { uid, map -> uid?.let { map[it] } }

    override suspend fun submitVerification(evidenceUris: List<String>, notes: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        b.verifications.update {
            it + (uid to VerificationRequest(uid, VerificationStatus.PENDING, notes, evidenceUris.size, b.clock.now(), null))
        }
        b.users.update { users ->
            val user = users[uid] ?: return@update users
            users + (uid to user.copy(verificationStatus = VerificationStatus.PENDING))
        }
        return AppResult.Success(Unit)
    }
}
