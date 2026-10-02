package cl.driverlink.app.domain

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.RegistrationData
import cl.driverlink.app.domain.model.SosType
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.repository.MessageRepository
import cl.driverlink.app.domain.usecase.CreateAlertUseCase
import cl.driverlink.app.domain.usecase.LoginUseCase
import cl.driverlink.app.domain.usecase.RegisterUseCase
import cl.driverlink.app.domain.usecase.RegistrationField
import cl.driverlink.app.domain.usecase.RegistrationValidator
import cl.driverlink.app.domain.usecase.SendAudioMessageUseCase
import cl.driverlink.app.domain.usecase.SendMessageUseCase
import cl.driverlink.app.domain.usecase.StartSosUseCase
import cl.driverlink.app.domain.usecase.VoteAlertUseCase
import cl.driverlink.app.testing.FakeAlertRepository
import cl.driverlink.app.testing.FakeAuthRepository
import cl.driverlink.app.testing.FakeConfigRepository
import cl.driverlink.app.testing.FakeSosRepository
import cl.driverlink.app.testing.FakeSubscriptionRepository
import cl.driverlink.app.testing.FixedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {
    private val valid = RegistrationData(
        firstName = " Camila ", lastName = "Rojas", email = "Camila@Mail.CL ", phone = "9 1234 5678",
        password = "clave1234", city = "Santiago", commune = "Ñuñoa", platforms = setOf(WorkPlatform.UBER),
    )

    @Test fun `registro valido normaliza datos`() = runTest {
        val auth = FakeAuthRepository()
        val result = RegisterUseCase(auth)(valid)
        assertTrue(result is AppResult.Success)
        val sent = auth.lastRegistration!!
        assertEquals("camila@mail.cl", sent.email)
        assertEquals("Camila", sent.firstName)
        assertEquals("+56912345678", sent.phone)
    }

    @Test fun `registro detecta campos invalidos`() {
        val errors = RegistrationValidator.validate(valid.copy(email = "x", platforms = emptySet(), password = "123"))
        assertEquals(setOf(RegistrationField.EMAIL, RegistrationField.PLATFORMS, RegistrationField.PASSWORD), errors)
    }

    @Test fun `login con correo invalido no llama al repositorio`() = runTest {
        val auth = FakeAuthRepository()
        val result = LoginUseCase(auth)("no-es-correo", "x")
        assertTrue(result is AppResult.Failure)
        assertEquals(0, auth.signInCalls)
    }
}

class AlertUseCasesTest {
    private val clock = FixedClock(10_000L)
    private val draft = AlertDraft(AlertCategory.TRAFFIC, "  Taco  ", GeoPoint(-33.4, -70.6), "Santiago", "Providencia")

    @Test fun `crear alerta calcula vencimiento desde configuracion`() = runTest {
        val repo = FakeAlertRepository()
        val config = FakeConfigRepository(AppConfig(alertExpirationMinutes = mapOf(AlertCategory.TRAFFIC to 20)))
        val result = CreateAlertUseCase(repo, config, clock)(draft)
        assertTrue(result is AppResult.Success)
        val (savedDraft, expiresAt) = repo.created!!
        assertEquals("Taco", savedDraft.description)
        assertEquals(10_000L + 20 * 60_000L, expiresAt)
    }

    @Test fun `no se crea alerta en mantencion`() = runTest {
        val config = FakeConfigRepository(AppConfig(maintenanceMode = true))
        val result = CreateAlertUseCase(FakeAlertRepository(), config, clock)(draft)
        assertEquals(AppResult.Failure(AppError.Maintenance), result)
    }

    @Test fun `descripcion demasiado larga es rechazada`() = runTest {
        val result = CreateAlertUseCase(FakeAlertRepository(), FakeConfigRepository(), clock)(draft.copy(description = "x".repeat(400)))
        assertTrue(result is AppResult.Failure)
    }

    @Test fun `no se permite votar dos veces`() = runTest {
        val repo = FakeAlertRepository()
        val vote = VoteAlertUseCase(repo)
        assertTrue(vote("a1", AlertVote.CONFIRM) is AppResult.Success)
        assertEquals(AppResult.Failure(AppError.AlreadyVoted), vote("a1", AlertVote.ENDED))
        assertEquals(AlertVote.CONFIRM, repo.votes["a1"])
    }
}

class SosUseCaseTest {
    private val clock = FixedClock(1_000_000L)

    @Test fun `usuario gratuito no puede iniciar SOS`() = runTest {
        val sos = FakeSosRepository()
        val useCase = StartSosUseCase(sos, FakeSubscriptionRepository(), FakeConfigRepository(), clock)
        assertEquals(AppResult.Failure(AppError.PremiumRequired), useCase(SosType.ACCIDENT, null, ""))
        assertEquals(0, sos.createCalls)
    }

    @Test fun `usuario pro inicia SOS`() = runTest {
        val sos = FakeSosRepository()
        val pro = Subscription(SubscriptionStatus.PRO, 0, clock.now() + 1_000_000, trialUsed = true, source = "play")
        val useCase = StartSosUseCase(sos, FakeSubscriptionRepository(pro), FakeConfigRepository(), clock)
        assertEquals(AppResult.Success("sos-1"), useCase(SosType.ROBBERY, GeoPoint(-33.0, -70.0), "nota"))
    }

    @Test fun `SOS deshabilitado por configuracion`() = runTest {
        val pro = Subscription(SubscriptionStatus.PRO, 0, null, trialUsed = true, source = "play")
        val useCase = StartSosUseCase(FakeSosRepository(), FakeSubscriptionRepository(pro), FakeConfigRepository(AppConfig(sosEnabled = false)), clock)
        assertEquals(AppResult.Failure(AppError.FeatureDisabled), useCase(SosType.OTHER, null, ""))
    }
}

class ChatUseCasesTest {
    private class RecordingMessageRepository : MessageRepository {
        val texts = mutableListOf<String>()
        val audios = mutableListOf<AudioClip>()
        override fun observeLatest(key: ChannelKey, limit: Int): Flow<List<Message>> = flowOf(emptyList())
        override suspend fun loadOlder(key: ChannelKey, beforeCreatedAt: Long, limit: Int) = AppResult.Success(emptyList<Message>())
        override suspend fun sendText(key: ChannelKey, text: String, replyTo: Message?): AppResult<Unit> {
            texts += text; return AppResult.Success(Unit)
        }
        override suspend fun sendAudio(key: ChannelKey, clip: AudioClip): AppResult<Unit> {
            audios += clip; return AppResult.Success(Unit)
        }
        override suspend fun deleteOwn(key: ChannelKey, messageId: String) = AppResult.Success(Unit)
    }

    private val key = ChannelKey("santiago")

    @Test fun `mensaje vacio no se envia`() = runTest {
        val repo = RecordingMessageRepository()
        assertTrue(SendMessageUseCase(repo)(key, "   ", null) is AppResult.Failure)
        assertTrue(SendMessageUseCase(repo)(key, " hola ", null) is AppResult.Success)
        assertEquals(listOf("hola"), repo.texts)
    }

    @Test fun `audio respeta duracion maxima configurable`() = runTest {
        val repo = RecordingMessageRepository()
        val config = FakeConfigRepository(AppConfig(maxAudioDurationSeconds = 10))
        val send = SendAudioMessageUseCase(repo, config)
        assertTrue(send(key, AudioClip("a.m4a", 300)) is AppResult.Failure)
        assertTrue(send(key, AudioClip("b.m4a", 15_000)) is AppResult.Failure)
        assertTrue(send(key, AudioClip("c.m4a", 9_000)) is AppResult.Success)
        assertEquals(1, repo.audios.size)
    }
}
