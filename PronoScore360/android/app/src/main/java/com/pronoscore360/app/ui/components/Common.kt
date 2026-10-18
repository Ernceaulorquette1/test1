package com.pronoscore360.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronoscore360.app.ui.theme.*
import com.pronoscore360.app.vm.UiState
import com.pronoscore360.core.model.Fixture
import com.pronoscore360.core.util.CATEGORY_LABEL
import com.pronoscore360.core.util.pct
import kotlinx.serialization.json.*

@Composable fun LoadingBox(label: String = "Cargando…") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text(label, color = Gray)
    }
}

@Composable fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Algo salió mal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable fun EmptyBox(title: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚽🏀", fontSize = 36.sp); Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (hint != null) Text(hint, color = Gray, textAlign = TextAlign.Center)
    }
}

/** Renderiza Loading/Error/Success de forma uniforme. */
@Composable fun <T> StateView(state: UiState<T>, onRetry: (() -> Unit)? = null, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Idle -> Unit
        UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message, onRetry)
        is UiState.Success -> content(state.data)
    }
}

@Composable fun ProbBar(value: Double, label: String? = null) {
    val color = when { value >= 0.70 -> Green; value >= 0.55 -> Yellow; else -> Gray }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "${label ?: "Probabilidad"}: ${pct(value)}" }) {
        if (label != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 13.sp); Text(pct(value), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color))
        }
    }
}

@Composable fun CategoryChip(category: String) {
    val color = when (category) { "high_probability" -> Green; "medium_probability" -> Yellow; "high_uncertainty" -> Red; else -> Gray }
    Text(CATEGORY_LABEL[category] ?: category, color = Color.White, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable fun TeamCircle(name: String) {
    val ini = name.removePrefix("Demo ").split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
    Box(Modifier.size(36.dp).clip(CircleShape).background(Navy2), contentAlignment = Alignment.Center) { Text(ini, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable fun DemoTag() { Text("DEMO", fontSize = 10.sp, color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Yellow).padding(horizontal = 6.dp, vertical = 1.dp)) }

@Composable fun MatchCard(f: Fixture, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(f.league.name, color = Gray, fontSize = 12.sp); if (f.isDemo) { Spacer(Modifier.width(4.dp)); DemoTag() } }
                Text((f.kickoffLocal ?: f.kickoff).take(16).replace("T", " "), color = Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            listOf(f.homeTeam to f.homeScore, f.awayTeam to f.awayScore).forEach { (t, s) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamCircle(t.name); Spacer(Modifier.width(10.dp)); Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (f.finished && s != null) Text("$s", fontWeight = FontWeight.Bold)
                }
            }
            Text(if (f.finished) "Finalizado" else "Ver análisis ›", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable fun UpgradeNotice(text: String = "Esta función es parte del plan Pro.", onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔒 $text", Modifier.weight(1f)); Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF04230F))) { Text("Ver Pro") }
        }
    }
}

@Composable fun Disclaimer() { Text("Estimaciones estadísticas informativas; no garantizan resultados. PronoScore360 no permite apuestas.", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp)) }

@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() } }
}

// ---- utilidades JSON (respuestas flexibles del backend) ----
fun JsonElement?.obj(k: String): JsonElement? = (this as? JsonObject)?.get(k)
fun JsonElement?.arr(k: String): List<JsonElement> = (obj(k) as? JsonArray)?.toList() ?: emptyList()
fun JsonElement?.str(k: String): String? = (obj(k) as? JsonPrimitive)?.contentOrNull
fun JsonElement?.dbl(k: String): Double? = (obj(k) as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.int(k: String): Int? = (obj(k) as? JsonPrimitive)?.intOrNull
fun JsonElement?.bool(k: String): Boolean? = (obj(k) as? JsonPrimitive)?.booleanOrNull
