package com.pronoscore360.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pronoscore360.app.ui.components.*
import com.pronoscore360.app.ui.theme.Gray
import com.pronoscore360.app.vm.ListsViewModel
import com.pronoscore360.app.vm.UiState
import com.pronoscore360.core.model.User
import com.pronoscore360.core.util.marketLabel
import com.pronoscore360.core.util.pct

@Composable private fun Screen(title: String, content: LazyListScopeContent) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        content()
    }
}
private typealias LazyListScopeContent = androidx.compose.foundation.lazy.LazyListScope.() -> Unit

@Composable fun PredictionsScreen(user: User, onMatch: (Long) -> Unit, onPlans: () -> Unit, vm: ListsViewModel = hiltViewModel()) {
    var minP by remember { mutableFloatStateOf(0.6f) }; var sport by remember { mutableStateOf<String?>(null) }; var unc by remember { mutableStateOf<String?>(null) }
    val state by vm.predictions.collectAsState()
    LaunchedEffect(minP, sport, unc) { if (user.isPro) vm.loadPredictions(sport, minP.toDouble(), unc) }
    Screen("Pronósticos destacados") {
        if (!user.isPro) { item { UpgradeNotice("El listado completo con filtros de probabilidad y calidad es del plan Pro.", onPlans) }; return@Screen }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sport == null, { sport = null }, label = { Text("Todos") }); FilterChip(sport == "football", { sport = "football" }, label = { Text("Fútbol") }); FilterChip(sport == "basketball", { sport = "basketball" }, label = { Text("NBA") })
            }
            Text("Probabilidad mínima: ${(minP * 100).toInt()} %")
            Slider(value = minP, onValueChange = { minP = it }, valueRange = 0.60f..0.95f, steps = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(null to "Cualquiera", "low" to "Incert. baja", "medium" to "Media").forEach { (k, l) -> FilterChip(unc == k, { unc = k }, label = { Text(l) }) } }
            Text("Los filtros nunca fuerzan resultados: si nada supera el umbral, la lista queda vacía.", color = Gray, fontSize = 12.sp)
        }
        item { StateView(state, { vm.loadPredictions(sport, minP.toDouble(), unc) }) { j -> if (j.arr("items").isEmpty()) EmptyBox("Ningún pronóstico supera estos filtros") } }
        (state as? UiState.Success)?.let { s -> items(s.data.arr("items")) { p ->
            val fx = p.obj("fixture"); val home = fx.obj("home_team").str("name") ?: ""; val away = fx.obj("away_team").str("name") ?: ""
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { fx.obj("id")?.let { onMatch(it.toString().toLong()) } }) {
                Column(Modifier.padding(12.dp)) {
                    Text("$home vs $away", fontWeight = FontWeight.SemiBold)
                    Row { Text(marketLabel(p.str("market") ?: "", p.str("selection") ?: "", home, away), Modifier.weight(1f)); CategoryChip(p.str("category") ?: "") }
                    ProbBar(p.dbl("probability") ?: 0.0, null)
                    Text("${pct(p.dbl("probability") ?: 0.0)} · ${p.int("data_points")} partidos usados", color = Gray, fontSize = 12.sp)
                }
            }
        } }
        item { Disclaimer() }
    }
}

@Composable fun SelectionsScreen(user: User, onPlans: () -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val basket by vm.basketItems.collectAsState(); val ev by vm.evaluation.collectAsState(); val groups by vm.groups.collectAsState(); val msg by vm.message.collectAsState()
    var name by remember { mutableStateOf("Mi selección") }
    LaunchedEffect(Unit) { if (user.isPro) vm.loadGroups() }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { host.showSnackbar(it); vm.clearMessage() } }
    Scaffold(snackbarHost = { SnackbarHost(host) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item { Text("Selecciones y combinadas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (!user.isPro) { item { UpgradeNotice("La herramienta de selecciones y probabilidad conjunta es parte del plan Pro.", onPlans) }; return@LazyColumn }
            item { Text("La probabilidad conjunta se calcula con el modelo; no se multiplican eventos correlacionados.", color = Gray, fontSize = 12.sp) }
            if (basket.isEmpty()) item { EmptyBox("Aún no hay selecciones", "Abre un partido, analízalo y pulsa «+ Selección».") }
            items(basket) { i ->
                val r = (ev as? UiState.Success)?.data?.items?.firstOrNull { it.key == i.key }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) { Text(i.match, fontWeight = FontWeight.SemiBold); Text(i.label, fontSize = 13.sp) }
                    Text(pct(r?.probability ?: i.probability), fontWeight = FontWeight.Bold); TextButton(onClick = { vm.removeFromBasket(i) }) { Text("✕") }
                } }
            }
            if (basket.isNotEmpty()) item {
                Button(onClick = { vm.evaluate() }) { Text("Calcular probabilidad conjunta") }
                StateView(ev, { vm.evaluate() }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (r.joint.computable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            if (r.joint.computable) { Text(pct(r.joint.probability ?: 0.0), fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Probabilidad conjunta estimada."); r.joint.notes.forEach { Text("• $it", fontSize = 12.sp) } }
                            else Text("⚠ ${r.joint.reason ?: "No se puede calcular con precisión la probabilidad total."}")
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { vm.saveBasket(name) }) { Text("Guardar") } }
            }
            item { Text("Guardadas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
            item { StateView(groups, { vm.loadGroups() }) { j -> if (j.arrOrEmpty().isEmpty()) Text("Sin selecciones guardadas.", color = Gray) } }
            (groups as? UiState.Success)?.let { s -> items(s.data.arrOrEmpty()) { g ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) {
                    Row { Text(g.str("name") ?: "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(when (g.str("result")) { "hit" -> "Acertada"; "miss" -> "Fallada"; else -> "Pendiente" }) }
                    Text(if (g.bool("joint_computable") == true) "Conjunta al guardar: ${pct(g.dbl("joint_probability_at_save") ?: 0.0)}" else "Conjunta no calculable con precisión", color = Gray, fontSize = 12.sp)
                    g.arr("items").forEach { Text("• ${it.obj("fixture").obj("home_team").str("name")} vs ${it.obj("fixture").obj("away_team").str("name")} — ${it.str("key")}", fontSize = 12.sp) }
                    TextButton(onClick = { g.int("id")?.let { vm.deleteGroup(it.toLong()) } }) { Text("Eliminar") }
                } }
            } }
            item { Disclaimer() }
        }
    }
}
private fun kotlinx.serialization.json.JsonElement.arrOrEmpty(): List<kotlinx.serialization.json.JsonElement> = (this as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()

@Composable fun FavoritesScreen(user: User, onMatch: (Long) -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val state by vm.favorites.collectAsState()
    LaunchedEffect(Unit) { vm.loadFavorites() }
    Screen("Favoritos") {
        item { StateView(state, { vm.loadFavorites() }) { j ->
            Text("${j.arr("items").size} de ${j.obj("limit") ?: "∞"} favoritos", color = Gray)
            if (j.arr("items").isEmpty()) EmptyBox("Aún no tienes favoritos", "Busca equipos y pulsa «Añadir a favoritos».")
        } }
        (state as? UiState.Success)?.let { s -> items(s.data.arr("items")) { f ->
            ListItem(headlineContent = { Text(f.obj("item").str("name") ?: (f.obj("item").obj("home_team").str("name") + " vs " + f.obj("item").obj("away_team").str("name"))) },
                supportingContent = { Text(f.str("kind") ?: "") }, trailingContent = { TextButton(onClick = { f.int("id")?.let { vm.removeFavorite(it.toLong()) } }) { Text("Quitar") } })
        } }
        if (!user.isPro) item { Text("Las alertas de partidos de tus favoritos son una función Pro.", color = Gray, fontSize = 12.sp) }
    }
}

@Composable fun HistoryScreen(user: User, onMatch: (Long) -> Unit, onPlans: () -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val state by vm.history.collectAsState(); val cmp by vm.comparison.collectAsState()
    LaunchedEffect(Unit) { vm.loadHistory(); if (user.isPro) vm.loadComparison() }
    Screen("Historial") {
        item { Text(if (user.plan.historyDays != null) "Últimos ${user.plan.historyDays} días (plan Gratis)" else "Historial completo", color = Gray) }
        item { StateView(state, { vm.loadHistory() }) { j -> if (j.arr("items").isEmpty()) EmptyBox("Aún no analizaste partidos") } }
        (state as? UiState.Success)?.let { s -> items(s.data.arr("items")) { h ->
            val f = h.obj("fixture")
            ListItem(headlineContent = { Text("${f.obj("home_team").str("name")} vs ${f.obj("away_team").str("name")}") }, supportingContent = { Text(h.str("date") ?: "") },
                modifier = Modifier.clickable { f.obj("id")?.let { onMatch(it.toString().toLong()) } })
        } }
        item { Text("Comparación de pronósticos y resultados", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
        if (!user.isPro) item { UpgradeNotice("Compara tus pronósticos guardados con los resultados reales.", onPlans) }
        else {
            item { StateView(cmp, { vm.loadComparison() }) { j ->
                val sm = j.obj("summary"); Text("Resueltos: ${sm.int("settled")} de ${sm.int("saved")} · Aciertos: ${sm.int("hits")}")
                if (j.arr("items").isEmpty()) EmptyBox("Guarda pronósticos (☆) desde un partido para compararlos aquí")
            } }
            (cmp as? UiState.Success)?.let { s -> items(s.data.arr("items")) { i ->
                val f = i.obj("fixture"); val home = f.obj("home_team").str("name") ?: ""; val away = f.obj("away_team").str("name") ?: ""
                ListItem(headlineContent = { Text("$home vs $away" + (i.str("final_score")?.let { " ($it)" } ?: "")) },
                    supportingContent = { Text("${marketLabel(i.str("market") ?: "", i.str("selection") ?: "", home, away)} · ${pct(i.dbl("probability") ?: 0.0)}") },
                    trailingContent = { Text(when (i.str("result")) { "hit" -> "✔ Acierto"; "miss" -> "✘ Fallo"; else -> "Pendiente" }) })
            } }
        }
    }
}

@Composable fun NotificationsScreen(vm: ListsViewModel = hiltViewModel()) {
    val state by vm.notifications.collectAsState()
    LaunchedEffect(Unit) { vm.loadNotifications() }
    Screen("Notificaciones") {
        item { TextButton(onClick = { vm.readAll() }) { Text("Marcar todas como leídas") } }
        item { StateView(state, { vm.loadNotifications() }) { p -> if (p.items.isEmpty()) EmptyBox("Sin notificaciones") } }
        (state as? UiState.Success)?.let { s -> items(s.data.items) { n ->
            ListItem(headlineContent = { Text(n.title, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold) }, supportingContent = { Text(n.body) }, overlineContent = { Text(n.createdAt.take(16).replace('T', ' ')) })
        } }
    }
}
