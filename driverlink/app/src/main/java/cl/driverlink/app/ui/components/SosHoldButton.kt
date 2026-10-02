package cl.driverlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.driverlink.app.R
import cl.driverlink.app.ui.theme.DriverLinkThemeExt
import kotlinx.coroutines.launch

/**
 * Botón SOS que exige mantener presionado [holdMillis] (≈3 s) para activarse,
 * evitando activaciones accidentales. Muestra el progreso y vibra al completarse.
 */
@Composable
fun SosHoldButton(
    holdMillis: Long,
    onActivated: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnActivated = rememberUpdatedState(onActivated)
    val description = stringResource(R.string.sos_hold_description)
    val colors = DriverLinkThemeExt.colors

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.sos)
            .semantics {
                role = Role.Button
                contentDescription = description
                // Accesibilidad: con lector de pantalla, doble toque abre la confirmación.
                onClick { currentOnActivated.value(); true }
            }
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(holdMillis.toInt(), easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnActivated.value()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    } else {
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(size - 12.dp),
            color = colors.onSos,
            strokeWidth = 8.dp,
            trackColor = colors.onSos.copy(alpha = 0.25f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS", color = colors.onSos, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.sos_hold_hint),
                color = colors.onSos,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
