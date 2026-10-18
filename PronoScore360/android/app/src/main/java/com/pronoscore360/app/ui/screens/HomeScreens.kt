package com.pronoscore360.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pronoscore360.app.ui.components.*
import com.pronoscore360.app.ui.theme.Gray
import com.pronoscore360.app.vm.CatalogViewModel
import com.pronoscore360.app.vm.ListsViewModel
import com.pronoscore360.core.model.User
import java.time.LocalDate

@Composable fun HomeScreen(user: User, onMatch: (Long) -> Unit, onSport: (String) -> Unit, onPlans: () -> Unit, vm: CatalogViewModel = hiltViewModel()) {
    val state by vm.fixtures.collectAsState()
    LaunchedEffect(Unit) { vm.loadFixtures(null, null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Hola, ${user.displayName} 👋", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val u = user.usage
                    Text(if (user.isPro) "Plan Pro: análisis ilimitados y todos los mercados." else "Plan Gratis: ${u.remaining} de ${u.limit} partidos disponibles hoy.", color = MaterialTheme.colorScheme.onPrimary)
                    if (!user.isPro && u.limit != null) LinearProgressIndicator(progress = { u.used.toFloat() / u.limit!! }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("Se renuevan a las 00:00 (${u.timezone}).", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSport("football") }, Modifier.weight(1f)) { Text("⚽ Fútbol") }
                OutlinedButton(onClick = { onSport("basketball") }, Modifier.weight(1f)) { Text("🏀 NBA") }
            }
            if (!user.isPro) UpgradeNotice("Pro: todos los mercados, marcadores y selecciones.", onPlans)
            Text("Próximos partidos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        item { StateView(state, { vm.loadFixtures(null, null) }) { p -> if (p.items.isEmpty()) EmptyBox("Sin partidos programados") } }
        (state as? com.pronoscore360.app.vm.UiState.Success)?.let { s -> items(s.data.items.filter { !it.finished }.take(8)) { f -> MatchCard(f) { onMatch(f.id) } } }
        item { Disclaimer() }
    }
}

@Composable fun SportSelectScreen(onSport: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Deportes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().clickable { onSport("football") }) { Column(Modifier.padding(20.dp)) { Text("⚽ Fútbol internacional", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Calendario, clasificación, goles, córners, tarjetas y marcadores.", color = Gray) } }
        Card(Modifier.fillMaxWidth().clickable { onSport("basketball") }) { Column(Modifier.padding(20.dp)) { Text("🏀 NBA y baloncesto", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Ganador, totales, hándicap, rangos y simulaciones.", color = Gray) } }
    }
}

@Composable fun CalendarScreen(sport: String, onMatch: (Long) -> Unit, vm: CatalogViewModel = hiltViewModel()) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val state by vm.fixtures.collectAsState()
    LaunchedEffect(sport, day) { vm.loadFixtures(sport, day.toString()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(if (sport == "football") "Calendario de fútbol" else "Calendario NBA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { day = day.minusDays(1) }) { Text("‹", fontSize = 24.sp) }
            TextButton(onClick = { day = LocalDate.now() }) { Text("$day  (hoy)") }
            IconButton(onClick = { day = day.plusDays(1) }) { Text("›", fontSize = 24.sp) }
        }
        LazyColumn { 
            item { StateView(state, { vm.loadFixtures(sport, day.toString()) }) { p -> if (p.items.isEmpty()) EmptyBox("No hay partidos este día", "Prueba otra fecha.") } }
            (state as? com.pronoscore360.app.vm.UiState.Success)?.let { s -> items(s.data.items) { f -> MatchCard(f) { onMatch(f.id) } } }
        }
    }
}

@Composable fun TeamsScreen(onMatch: (Long) -> Unit, catalog: CatalogViewModel = hiltViewModel(), lists: ListsViewModel = hiltViewModel()) {
    var q by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<Pair<Long, String>?>(null) }; var last by remember { mutableIntStateOf(10) }
    val teams by catalog.teams.collectAsState(); val stats by catalog.teamStats.collectAsState(); val msg by lists.message.collectAsState()
    LaunchedEffect(q) { catalog.searchTeams(q) }
    LaunchedEffect(selected, last) { selected?.let { catalog.loadTeamStats(it.first, last) } }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { host.showSnackbar(it); lists.clearMessage() } }
    Scaffold(snackbarHost = { SnackbarHost(host) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            item {
                Text("Equipos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(q, { q = it }, label = { Text("Buscar equipo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            selected?.let { (id, name) ->
                item {
                    Section(name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 20).forEach { n -> FilterChip(last == n, { last = n }, label = { Text("Últimos $n") }) } }
                        StateView(stats, { catalog.loadTeamStats(id, last) }) { j ->
                            Text("Partidos: ${j.int("matches")} · G ${j.int("wins")} · E ${j.int("draws")} · P ${j.int("losses")}")
                            Text("A favor / en contra (prom.): ${j.dbl("avg_for")} / ${j.dbl("avg_against")}")
                            j.obj("corners")?.let { Text("Córners fav/contra: ${it.dbl("avg_for")} / ${it.dbl("avg_against")}") }
                            j.obj("cards")?.let { Text("Tarjetas fav/contra: ${it.dbl("avg_for")} / ${it.dbl("avg_against")}") }
                            Text("Forma: " + j.arr("form").joinToString(" ") { it.toString().trim('"') })
                        }
                        TextButton(onClick = { lists.addFavorite("team", id) }) { Text("☆ Añadir a favoritos") }
                    }
                }
            }
            item { StateView(teams, { catalog.searchTeams(q) }) { p -> if (p.items.isEmpty()) EmptyBox("Sin resultados") } }
            (teams as? com.pronoscore360.app.vm.UiState.Success)?.let { s -> items(s.data.items) { t ->
                ListItem(headlineContent = { Text(t.name) }, leadingContent = { TeamCircle(t.name) }, modifier = Modifier.clickable { selected = t.id to t.name })
            } }
        }
    }
}
