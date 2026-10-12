package com.pronoscore360.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pronoscore360.app.ui.components.*
import com.pronoscore360.app.ui.theme.Gray
import com.pronoscore360.app.ui.theme.Green
import com.pronoscore360.app.vm.CatalogViewModel
import com.pronoscore360.app.vm.ListsViewModel
import com.pronoscore360.app.vm.SessionViewModel
import com.pronoscore360.app.vm.UiState
import com.pronoscore360.core.model.Analysis
import com.pronoscore360.core.model.Prediction
import com.pronoscore360.core.model.User
import com.pronoscore360.core.util.*
import kotlinx.serialization.json.JsonElement

@Composable
fun MatchScreen(fixtureId: Long, user: User, session: SessionViewModel, onPlans: () -> Unit, catalog: CatalogViewModel = hiltViewModel(), lists: ListsViewModel = hiltViewModel()) {
    val state by catalog.analysis.collectAsState()
    val basketItems by lists.basketItems.collectAsState()
    val msg by lists.message.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var fixtureHead by remember { mutableStateOf<com.pronoscore360.core.model.Fixture?>(null) }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(fixtureId) { catalog.resetAnalysis(); catalog.loadFixtureStats(fixtureId) }
    LaunchedEffect(msg) { msg?.let { host.showSnackbar(it); lists.clearMessage() } }
    val u = user.usage
    Scaffold(snackbarHost = { SnackbarHost(host) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            val a = (state as? UiState.Success<Analysis>)?.data
            item {
                if (a != null) {
                    val f = a.fixture
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f.league.name + if (f.isDemo) " · DEMO" else "", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                            Text("${f.homeTeam.name}  vs  ${f.awayTeam.name}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (f.finished) Text("${f.homeScore} - ${f.awayScore}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp)
                        }
                    }
                }
            }
            if (state is UiState.Idle) item {
                Section("Análisis del partido") {
                    Text(if (user.isPro) "Consultas ilimitadas con tu plan Pro." else "Usa 1 de tus ${u.limit} consultas diarias (te quedan ${u.remaining}). Abrir de nuevo el mismo partido hoy no descuenta otra.", color = Gray)
                    Button(onClick = { catalog.analyze(fixtureId) { session.refresh() } }, enabled = user.isPro || (u.remaining ?: 0) > 0, modifier = Modifier.padding(top = 8.dp)) { Text("Analizar partido") }
                }
            }
            item { StateView(state, { catalog.analyze(fixtureId) { session.refresh() } }) { } }
            val err = state as? UiState.Error
            if (err?.code == "quota_exceeded") item { UpgradeNotice(err.message, onPlans) }
            if (a != null && !a.ok) item { Section("Datos insuficientes") { Text(a.reason ?: "No hay datos suficientes para publicar un pronóstico.") } }
            if (a != null && a.ok) {
                item {
                    Text("Actualizado: ${a.analysis.generatedAt.take(16).replace('T', ' ')} · Datos: ${a.analysis.nHome}/${a.analysis.nAway} partidos previos · Calidad: ${QUALITY_LABEL[a.analysis.quality]}", color = Gray, fontSize = 12.sp)
                    if (!a.analysis.isPreMatch) Text("⚠ Análisis posterior al inicio (no es una predicción previa)", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) { listOf("Pronósticos", "Marcadores", "Estadísticas", "Explicación").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
                }
                when (tab) {
                    0 -> {
                        val groups = a.predictions.map { it.group }.distinct()
                        groups.forEach { g -> item { Section(GROUP_LABEL[g] ?: g) {
                            a.predictions.filter { it.group == g }.forEach { p -> PredictionRow(p, a, user, basketItems.any { it.fixtureId == a.fixture.id && it.key == p.key }, lists) }
                        } } }
                        if (a.lockedGroups.isNotEmpty()) item { UpgradeNotice("Bloqueado en tu plan: ${a.lockedGroups.joinToString { GROUP_LABEL[it] ?: it }}", onPlans) }
                    }
                    1 -> item { ScoreSection(a, onPlans) }
                    2 -> item { StatsSection(catalog, a) }
                    else -> item { WhySection(a, onPlans) }
                }
                item { Disclaimer() }
            }
        }
    }
}

@Composable private fun PredictionRow(p: Prediction, a: Analysis, user: User, inBasket: Boolean, lists: ListsViewModel) {
    val label = marketLabel(p.market, p.selection, a.fixture.homeTeam.name, a.fixture.awayTeam.name)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f)); CategoryChip(p.category)
        }
        ProbBar(p.probability)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${pct(p.probability)} · ${p.dataPoints} partidos", color = Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (user.isPro && p.key != null) {
                TextButton(onClick = { lists.toggleBasket(BasketItem(a.fixture.id, p.key!!, label, "${a.fixture.homeTeam.name} vs ${a.fixture.awayTeam.name}", p.probability)) }) { Text(if (inBasket) "✓ En selección" else "+ Selección") }
                TextButton(onClick = { lists.savePrediction(p.id) }) { Text("☆") }
            }
        }
    }
}

@Composable private fun ScoreSection(a: Analysis, onPlans: () -> Unit) {
    val s = a.exactScore ?: return Text("Datos insuficientes para estimar marcadores.")
    if (s.str("type") == "football") {
        val top = s.arr("top_scores")
        val best = s.obj("most_likely")
        Section("Marcador más probable: ${best.int("home")}-${best.int("away")} (${pct(best.dbl("probability") ?: 0.0)})") {
            Text("Goles esperados: ${a.fixture.homeTeam.name} ${s.obj("expected_goals").dbl("home")} · ${a.fixture.awayTeam.name} ${s.obj("expected_goals").dbl("away")}")
            Spacer(Modifier.height(8.dp))
            BarChart(top.map { "${it.int("home")}-${it.int("away")}" to (it.dbl("probability") ?: 0.0) })
            top.forEach { Text("${it.int("home")}-${it.int("away")} → probabilidad de acertarlo exacto: ${pct(it.dbl("probability") ?: 0.0, 2)}", fontSize = 13.sp) }
            if ((s.int("locked_extra_scores") ?: 0) > 0) UpgradeNotice("Pro muestra los 5 marcadores más probables.", onPlans)
            Text(s.str("note") ?: "", color = Gray, fontSize = 12.sp)
        }
    } else {
        val c = s.obj("central_score")
        Section("Puntos esperados") {
            Text("${a.fixture.homeTeam.name} ${s.obj("expected_points").dbl("home")} · ${a.fixture.awayTeam.name} ${s.obj("expected_points").dbl("away")}", fontWeight = FontWeight.Bold)
            Text("Marcador central estimado: ${c.int("home")}-${c.int("away")} (ESTIMACIÓN; probabilidad de acertarlo exacto: ${pct(c.dbl("exact_hit_probability") ?: 0.0, 2)})")
            ProbBar(s.dbl("p_home_win") ?: 0.0, "Gana ${a.fixture.homeTeam.name}"); Spacer(Modifier.height(6.dp)); ProbBar(s.dbl("p_away_win") ?: 0.0, "Gana ${a.fixture.awayTeam.name}")
            Text("Total esperado ${s.dbl("expected_total")} · diferencia esperada ${s.dbl("expected_margin")}", Modifier.padding(top = 6.dp))
            if (s.obj("range_total_80") != null) {
                val r = s.arr("range_total_80"); Text("Rango probable del total (80 %): ${r.getOrNull(0)}–${r.getOrNull(1)}")
                BarChart(s.arr("total_distribution").map { it.int("from").toString() to (it.dbl("probability") ?: 0.0) })
            } else UpgradeNotice("Pro muestra rangos probables y distribución de puntos.", onPlans)
            Text(s.str("note") ?: "", color = Gray, fontSize = 12.sp)
        }
    }
}

@Composable private fun BarChart(data: List<Pair<String, Double>>) {
    if (data.isEmpty()) return
    val max = data.maxOf { it.second }.coerceAtLeast(0.0001)
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width / data.size
            data.forEachIndexed { i, (_, v) ->
                val h = (v / max).toFloat() * size.height
                drawRect(Green, topLeft = Offset(i * w + w * 0.15f, size.height - h), size = Size(w * 0.7f, h))
            }
        }
        Row(Modifier.fillMaxWidth()) { data.forEach { Text(it.first, Modifier.weight(1f), fontSize = 10.sp, color = Gray) } }
    }
}

@Composable private fun StatsSection(catalog: CatalogViewModel, a: Analysis) {
    val st by catalog.fixtureStats.collectAsState()
    var n by remember { mutableStateOf("10") }
    StateView(st, { catalog.loadFixtureStats(a.fixture.id) }) { j ->
        Section("Forma reciente") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("5", "10", "20").forEach { k -> FilterChip(n == k, { n = k }, label = { Text("Últimos $k") }) } }
            val form = j.obj("form").obj(n)
            listOf(a.fixture.homeTeam.name to form.obj("home_team"), a.fixture.awayTeam.name to form.obj("away_team")).forEach { (name, t) ->
                Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("G ${t.int("wins")} · E ${t.int("draws")} · P ${t.int("losses")} (${t.int("matches")} partidos) · prom. ${t.dbl("avg_for")} a favor / ${t.dbl("avg_against")} en contra")
                t.obj("corners")?.let { Text("Córners fav/contra: ${it.dbl("avg_for")} / ${it.dbl("avg_against")}", fontSize = 13.sp) }
                t.obj("cards")?.let { Text("Tarjetas fav/contra: ${it.dbl("avg_for")} / ${it.dbl("avg_against")}", fontSize = 13.sp) }
            }
            Text("Enfrentamientos directos", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            val h2h = j.arr("head_to_head")
            if (h2h.isEmpty()) Text("Sin enfrentamientos previos registrados.", color = Gray) else h2h.forEach { Text("${it.str("date")?.take(10)}: ${it.str("home")} ${it.str("score")} ${it.str("away")}", fontSize = 13.sp) }
        }
    }
}

@Composable private fun WhySection(a: Analysis, onPlans: () -> Unit) {
    Section("¿Cómo se calculó?") {
        Text(a.exactScore.str("explanation") ?: "", fontSize = 13.sp)
        Text("Modelo: ${a.analysis.model} v${a.analysis.modelVersion}" + if (a.analysis.isDemo) " · datos DEMO" else "", color = Gray, fontSize = 12.sp)
        val d = a.drivers
        if (d == null) UpgradeNotice("Pro muestra las variables principales del modelo.", onPlans) else d.forEach { Text("• ${it.factor}: ${it.value.toString().trim('"')}", fontSize = 13.sp) }
        Text("Categorías", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        a.analysis.categoryCriteria.forEach { (k, v) -> Text("• ${CATEGORY_LABEL[k] ?: k}: $v", fontSize = 12.sp) }
    }
}
