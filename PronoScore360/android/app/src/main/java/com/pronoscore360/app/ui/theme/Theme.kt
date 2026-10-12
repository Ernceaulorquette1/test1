package com.pronoscore360.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0B1F3A); val Navy2 = Color(0xFF12305A); val Green = Color(0xFF1FBF75)
val Yellow = Color(0xFFF5B301); val Red = Color(0xFFE5484D); val Gray = Color(0xFF6B7A90)

private val Light = lightColorScheme(primary = Navy2, onPrimary = Color.White, secondary = Green, onSecondary = Color(0xFF04230F),
    tertiary = Yellow, error = Red, background = Color(0xFFF6F8FC), surface = Color.White, surfaceVariant = Color(0xFFE8EDF5))
private val Dark = darkColorScheme(primary = Color(0xFF5B9BF5), onPrimary = Color(0xFF031A3A), secondary = Green, onSecondary = Color(0xFF04230F),
    tertiary = Yellow, error = Color(0xFFFF7A7F), background = Color(0xFF0A1626), surface = Color(0xFF10233B), surfaceVariant = Color(0xFF1A3052))

@Composable
fun PronoTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
