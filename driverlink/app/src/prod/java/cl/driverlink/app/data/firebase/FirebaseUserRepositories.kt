package cl.driverlink.app.data.firebase

import android.net.Uri
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppException
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.AccountStatus
import cl.driverlink.app.domain.model.PlatformRole
import cl.driverlink.app.domain.model.ProfileUpdate
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.model.Reputation
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.VerificationRequest
import cl.driverlink.app.domain.model.VerificationStatus
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
) : AuthRepository {

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            trySend(if (uid == null) AuthState.SignedOut else AuthState.SignedIn(uid))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    /**
     * Crea la cuenta y el perfil. Las reglas de Firestore solo aceptan el perfil con
     * verificationStatus=UNVERIFIED, role=DRIVER y plan FREE; la Cloud Function
     * `onUserCreated` crea la suscripción y el perfil público.
     */
    override suspend fun register(data: RegistrationData): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val result = auth.createUserWithEmailAndPassword(data.email, data.password).await()
        val uid = result.user?.uid ?: throw AppException(AppError.Unknown())
        val photoUrl = data.photoUri?.let { uploadProfilePhoto(storage, uid, it) }
        db.collection(Paths.USERS).document(uid).set(
            mapOf(
                "firstName" to data.firstName,
                "lastName" to data.lastName,
                "email" to data.email,
                "phone" to data.phone,
                "photoUrl" to photoUrl,
                "city" to data.city,
                "commune" to data.commune,
                "platforms" to data.platforms.map { it.name },
                "verificationStatus" to VerificationStatus.UNVERIFIED.name,
                "accountStatus" to AccountStatus.ACTIVE.name,
                "subscriptionStatus" to SubscriptionStatus.FREE.name,
                "role" to PlatformRole.DRIVER.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    /** La Cloud Function `deleteAccount` borra datos personales y luego la cuenta de Auth. */
    override suspend fun deleteAccount(): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        functions.getHttpsCallable("deleteAccount").call().await()
        auth.signOut()
    }
}

suspend fun uploadProfilePhoto(storage: FirebaseStorage, uid: String, uri: String): String {
    val ref = storage.reference.child("profilePhotos/$uid/avatar.jpg")
    ref.putFile(Uri.parse(uri)).await()
    return ref.downloadUrl.await().toString()
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseUserRepository(
    private val auth: FirebaseAuthRepository,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val crash: CrashReporter,
) : UserRepository {

    override fun observeCurrentUser(): Flow<User?> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(null)
        else db.collection(Paths.USERS).document(state.userId).observe(crash, null) { it.toUser() }
    }

    /** Solo campos editables por el usuario; las reglas rechazan cualquier otro. */
    override suspend fun updateProfile(update: ProfileUpdate): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        val fields = mutableMapOf<String, Any?>(
            "firstName" to update.firstName,
            "lastName" to update.lastName,
            "phone" to update.phone,
            "city" to update.city,
            "commune" to update.commune,
            "platforms" to update.platforms.map { it.name },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        update.newPhotoUri?.let { fields["photoUrl"] = uploadProfilePhoto(storage, uid, it) }
        db.collection(Paths.USERS).document(uid).update(fields).await()
        Unit
    }

    override fun observeVerification(): Flow<VerificationRequest?> = auth.authState.flatMapLatest { state ->
        if (state !is AuthState.SignedIn) flowOf(null)
        else db.collection(Paths.VERIFICATION_REQUESTS).document(state.userId).observe(crash, null) { doc ->
            if (!doc.exists()) null
            else VerificationRequest(
                userId = doc.id,
                status = doc.enum("status", VerificationStatus.PENDING),
                notes = doc.str("notes"),
                evidenceCount = (doc.get("evidencePaths") as? List<*>)?.size ?: 0,
                submittedAt = doc.millis("submittedAt"),
                reviewerComment = doc.getString("reviewerComment"),
            )
        }
    }

    /**
     * La evidencia se sube a `verification/{uid}/` (solo el dueño puede escribir y solo
     * moderadores/administradores pueden leer). El estado lo cambia el servidor.
     */
    override suspend fun submitVerification(evidenceUris: List<String>, notes: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val uid = auth.currentUserId() ?: throw AppException(AppError.Unauthenticated)
        val paths = evidenceUris.mapIndexed { index, uri ->
            val path = "verification/$uid/evidence_${System.currentTimeMillis()}_$index"
            storage.reference.child(path).putFile(Uri.parse(uri)).await()
            path
        }
        db.collection(Paths.VERIFICATION_REQUESTS).document(uid).set(
            mapOf(
                "userId" to uid,
                "status" to VerificationStatus.PENDING.name,
                "notes" to notes,
                "evidencePaths" to paths,
                "submittedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }
}

fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    @Suppress("UNCHECKED_CAST")
    val reputation = get("reputation") as? Map<String, Any?>
    return User(
        id = id,
        firstName = str("firstName"),
        lastName = str("lastName"),
        email = str("email"),
        phone = str("phone"),
        photoUrl = getString("photoUrl"),
        city = str("city"),
        commune = str("commune"),
        platforms = (get("platforms") as? List<*>)?.mapNotNull { WorkPlatform.fromId(it.toString()) } ?: emptyList(),
        verificationStatus = enum("verificationStatus", VerificationStatus.UNVERIFIED),
        accountStatus = enum("accountStatus", AccountStatus.ACTIVE),
        subscriptionStatus = enum("subscriptionStatus", SubscriptionStatus.FREE),
        role = enum("role", PlatformRole.DRIVER),
        reputation = Reputation(
            alertsCreated = (reputation?.get("alertsCreated") as? Number)?.toInt() ?: 0,
            alertsConfirmed = (reputation?.get("alertsConfirmed") as? Number)?.toInt() ?: 0,
            usefulReports = (reputation?.get("usefulReports") as? Number)?.toInt() ?: 0,
            score = (reputation?.get("score") as? Number)?.toInt() ?: 0,
        ),
        createdAt = millis("createdAt") ?: 0L,
        updatedAt = millis("updatedAt") ?: 0L,
    )
}
