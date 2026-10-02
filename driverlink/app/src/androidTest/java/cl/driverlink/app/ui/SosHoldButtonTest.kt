package cl.driverlink.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertHasClickAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.driverlink.app.ui.components.SosHoldButton
import cl.driverlink.app.ui.theme.DriverLinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de UI: el SOS solo se activa al mantener presionado (o por accesibilidad). */
@RunWith(AndroidJUnit4::class)
class SosHoldButtonTest {

    @get:Rule val compose = createComposeRule()

    private val description = "Botón SOS. Mantén presionado tres segundos para pedir asistencia."

    @Test
    fun toqueCortoNoActiva() {
        var activations = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DriverLinkTheme { SosHoldButton(holdMillis = 3_000, onActivated = { activations++ }) } }
        compose.onNodeWithContentDescription(description).performTouchInput {
            down(center)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription(description).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(0, activations)
    }

    @Test
    fun mantenerTresSegundosActiva() {
        var activations = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DriverLinkTheme { SosHoldButton(holdMillis = 3_000, onActivated = { activations++ }) } }
        compose.onNodeWithContentDescription(description).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithContentDescription(description).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, activations)
    }

    @Test
    fun accesibleConLectorDePantalla() {
        var activations = 0
        compose.setContent { DriverLinkTheme { SosHoldButton(holdMillis = 3_000, onActivated = { activations++ }) } }
        compose.onNodeWithContentDescription(description).assertHasClickAction().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, activations)
    }
}
