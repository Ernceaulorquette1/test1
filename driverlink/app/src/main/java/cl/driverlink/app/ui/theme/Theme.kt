package cl.driverlink.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colores semánticos que Material 3 no cubre (SOS, éxito, advertencia). */
@Immutable
data class ExtendedColors(
    val sos: Color,
    val onSos: Color,
    val success: Color,
    val warning: Color,
)

private val LightExtended = ExtendedColors(SosRed, OnSos, SuccessGreen, WarningOrange)
private val DarkExtended = ExtendedColors(SosRedDark, Color.Black, SuccessGreenDark, WarningOrangeDark)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandTeal,
    onSecondary = Color.White,
    tertiary = BrandAmber,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    error = SosRed,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF00205F),
    secondary = BrandTealDark,
    onSecondary = Color(0xFF00382F),
    tertiary = BrandAmber,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    error = SosRedDark,
)

@Composable
fun DriverLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DriverLinkTypography,
            shapes = DriverLinkShapes,
            content = content,
        )
    }
}

object DriverLinkThemeExt {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
