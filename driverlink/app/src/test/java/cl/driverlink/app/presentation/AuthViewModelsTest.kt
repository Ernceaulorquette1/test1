package cl.driverlink.app.presentation

import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.usecase.LoginUseCase
import cl.driverlink.app.domain.usecase.RegisterUseCase
import cl.driverlink.app.domain.usecase.RegistrationField
import cl.driverlink.app.presentation.auth.LoginViewModel
import cl.driverlink.app.presentation.auth.RegisterViewModel
import cl.driverlink.app.testing.FakeAnalytics
import cl.driverlink.app.testing.FakeAuthRepository
import cl.driverlink.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val analytics = FakeAnalytics()
    private val viewModel = LoginViewModel(LoginUseCase(auth), auth, analytics)

    @Test fun `login exitoso registra evento`() {
        viewModel.onEmailChange("demo@driverlink.cl")
        viewModel.onPasswordChange("demo1234")
        viewModel.submit()
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf(AnalyticsEvent.LOGIN_COMPLETED), analytics.events)
    }

    @Test fun `credenciales incorrectas muestran mensaje comprensible`() {
        auth.signInResult = AppResult.Failure(AppError.InvalidCredentials)
        viewModel.onEmailChange("demo@driverlink.cl")
        viewModel.onPasswordChange("mala")
        viewModel.submit()
        assertEquals(UiText.Res(R.string.error_invalid_credentials), viewModel.state.value.error)
        assertTrue(analytics.events.isEmpty())
    }

    @Test fun `correo invalido no consulta el servidor`() {
        viewModel.onEmailChange("x")
        viewModel.submit()
        assertEquals(UiText.Res(R.string.error_email_invalid), viewModel.state.value.error)
        assertEquals(0, auth.signInCalls)
    }
}

class RegisterViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = RegisterViewModel(RegisterUseCase(auth), FakeAnalytics())

    @Test fun `formulario incompleto marca errores`() {
        viewModel.submit()
        val state = viewModel.state.value
        assertTrue(RegistrationField.EMAIL in state.fieldErrors)
        assertNotNull(state.error)
        assertNull(auth.lastRegistration)
    }

    @Test fun `exige aceptar terminos`() {
        fill()
        viewModel.submit()
        assertEquals(UiText.Res(R.string.register_accept_terms_required), viewModel.state.value.error)
        assertNull(auth.lastRegistration)
    }

    @Test fun `registro completo llama al repositorio`() {
        fill()
        viewModel.update { it.copy(acceptedTerms = true) }
        viewModel.submit()
        assertEquals("ana@driverlink.cl", auth.lastRegistration?.email)
        assertEquals(setOf(WorkPlatform.DIDI), auth.lastRegistration?.platforms)
    }

    private fun fill() {
        viewModel.update {
            it.copy(firstName = "Ana", lastName = "Muñoz", email = "ana@driverlink.cl", phone = "912345678",
                password = "clave1234", city = "Santiago", commune = "Maipú")
        }
        viewModel.togglePlatform(WorkPlatform.DIDI)
    }
}
