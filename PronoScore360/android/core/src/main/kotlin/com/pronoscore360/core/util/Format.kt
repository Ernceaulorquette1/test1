package com.pronoscore360.core.util

import java.util.Locale

/** Porcentaje con una decimal tal cual lo estima el modelo (sin redondear hacia arriba). */
fun pct(p: Double, decimals: Int = 1): String = String.format(Locale.ROOT, "%.${decimals}f %%", p * 100)

val GROUP_LABEL = mapOf("basic" to "Principales", "goals" to "Goles", "corners" to "Córners", "cards" to "Tarjetas", "halftime" to "Descanso", "nba_lines" to "Líneas NBA (puntos)")
val CATEGORY_LABEL = mapOf("high_probability" to "Alta probabilidad estimada", "medium_probability" to "Probabilidad media",
    "high_uncertainty" to "Alta incertidumbre", "insufficient_data" to "Datos insuficientes")
val QUALITY_LABEL = mapOf("high" to "Alta (≥20 partidos)", "medium" to "Media (10-19)", "low" to "Baja (5-9)", "insufficient" to "Insuficiente (<5)")

fun marketLabel(market: String, selection: String, home: String = "Local", away: String = "Visitante"): String {
    val p = market.split(":")
    fun side(s: String) = when (s) { "home" -> home; "away" -> away; "draw" -> "Empate"; else -> s }
    fun ou(s: String, line: String) = (if (s == "over") "Más de " else "Menos de ") + line
    return when (p[0]) {
        "1X2" -> if (selection == "draw") "Empate" else "Victoria ${side(selection)}"
        "DC" -> mapOf("1X" to "$home o empate", "12" to "$home o $away", "X2" to "Empate o $away")[selection] ?: selection
        "BTTS" -> "Ambos equipos marcan: " + if (selection == "yes") "Sí" else "No"
        "OU" -> "Goles totales: ${ou(selection, p[1])}"
        "CORNERS_TOTAL" -> "Córners totales: ${ou(selection, p[1])}"
        "CORNERS_HOME" -> "Córners $home: ${ou(selection, p[1])}"
        "CORNERS_AWAY" -> "Córners $away: ${ou(selection, p[1])}"
        "CARDS_TOTAL" -> "Tarjetas totales: ${ou(selection, p[1])}"
        "CARDS_HOME" -> "Tarjetas $home: ${ou(selection, p[1])}"
        "CARDS_AWAY" -> "Tarjetas $away: ${ou(selection, p[1])}"
        "HT_1X2" -> "Descanso: " + if (selection == "draw") "Empate" else "Gana ${side(selection)}"
        "ML" -> "Ganador: ${side(selection)}"
        "TOTAL" -> "Puntos totales: ${ou(selection, p[1])}"
        "TEAM_TOTAL" -> "Puntos ${side(p[1])}: ${ou(selection, p[2])}"
        "SPREAD" -> "$home ${if (p[2].toDouble() > 0) "+" else ""}${p[2]} (hándicap): " + if (selection == "cover") "cubre" else "no cubre"
        else -> "$market $selection"
    }
}
