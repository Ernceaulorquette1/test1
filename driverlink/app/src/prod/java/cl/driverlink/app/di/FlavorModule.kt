package cl.driverlink.app.di

import android.content.Context
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.data.firebase.ConfigBillingGateway
import cl.driverlink.app.data.firebase.CrashlyticsReporter
import cl.driverlink.app.data.firebase.FUNCTIONS_REGION
import cl.driverlink.app.data.firebase.FcmSync
import cl.driverlink.app.data.firebase.FirebaseAlertRepository
import cl.driverlink.app.data.firebase.FirebaseAnalyticsTracker
import cl.driverlink.app.data.firebase.FirebaseAuthRepository
import cl.driverlink.app.data.firebase.FirebaseChannelRepository
import cl.driverlink.app.data.firebase.FirebaseCompanyRepository
import cl.driverlink.app.data.firebase.FirebaseConfigRepository
import cl.driverlink.app.data.firebase.FirebaseEmergencyContactRepository
import cl.driverlink.app.data.firebase.FirebaseMessageRepository
import cl.driverlink.app.data.firebase.FirebaseModerationRepository
import cl.driverlink.app.data.firebase.FirebaseNotificationPreferencesRepository
import cl.driverlink.app.data.firebase.FirebaseSosRepository
import cl.driverlink.app.data.firebase.FirebaseSubscriptionRepository
import cl.driverlink.app.data.firebase.FirebaseUserRepository
import cl.driverlink.app.data.firebase.FirebaseVehicleRepository
import cl.driverlink.app.data.firebase.FirebaseWorkSessionRepository
import cl.driverlink.app.domain.auth.ExternalAuthRegistry
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.services.notifications.LocalNotifier
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope

/** Flavor prod: repositorios Firebase. Requiere app/google-services.json (ver FIREBASE_SETUP.md). */
object FlavorModule {

    fun create(context: Context, clock: Clock, scope: CoroutineScope, notifier: LocalNotifier): DataModule {
        check(FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null) {
            "Firebase no está configurado: agrega app/google-services.json (ver FIREBASE_SETUP.md)"
        }
        val db = FirebaseFirestore.getInstance().apply {
            // Caché persistente: lectura offline y escrituras encoladas sin conexión.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(CACHE_BYTES).build())
                .build()
        }
        val firebaseAuth = FirebaseAuth.getInstance()
        val storage = FirebaseStorage.getInstance()
        val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
        val crash = CrashlyticsReporter()
        val config = FirebaseConfigRepository(FirebaseRemoteConfig.getInstance(), crash)

        val auth = FirebaseAuthRepository(firebaseAuth, db, storage, functions)
        val users = FirebaseUserRepository(auth, db, storage, crash)
        val sos = FirebaseSosRepository(db, functions, auth, crash)
        val prefs = FirebaseNotificationPreferencesRepository(db, auth, crash)
        FcmSync(db, auth, users, prefs, crash).start(scope)

        return object : DataModule {
            override val authRepository = auth
            override val userRepository = users
            override val channelRepository = FirebaseChannelRepository(db, crash)
            override val messageRepository = FirebaseMessageRepository(db, storage, users, crash)
            override val alertRepository = FirebaseAlertRepository(db, auth, users, crash)
            override val sosRepository = sos
            override val sosOperatorRepository = sos
            override val emergencyContactRepository = FirebaseEmergencyContactRepository(db, auth, crash)
            override val subscriptionRepository = FirebaseSubscriptionRepository(db, functions, auth, crash)
            override val billingGateway = ConfigBillingGateway(config)
            override val moderationRepository = FirebaseModerationRepository(db, auth, crash)
            override val notificationPreferencesRepository = prefs
            override val configRepository = config
            override val companyRepository = FirebaseCompanyRepository(db, functions, auth, crash)
            override val vehicleRepository = FirebaseVehicleRepository(db, crash)
            override val workSessionRepository = FirebaseWorkSessionRepository(db, auth, crash)
            override val analytics = FirebaseAnalyticsTracker(context)
            override val crashReporter = crash
            override val externalAuthRegistry = ExternalAuthRegistry()
            override val locationFallback: GeoPoint? = null
        }
    }

    private const val CACHE_BYTES = 50L * 1024 * 1024
}
