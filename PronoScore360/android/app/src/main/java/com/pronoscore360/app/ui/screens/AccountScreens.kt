package com.pronoscore360.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pronoscore360.app.BuildConfig
import com.pronoscore360.app.ui.components.*
import com.pronoscore360.app.ui.theme.Gray
import com.pronoscore360.app.vm.PlansViewModel
import com.pronoscore360.app.vm.SessionViewModel
import com.pronoscore360.core.model.Plan
import com.pronoscore360.core.model.User

private fun priceFor(plan: Plan, country: String): String {
    val p = plan.prices[country] ?: plan.prices["DEFAULT"] ?: return "—"
    return if (p.currency == "CLP") "$" + "%,d".format(java.util.Locale("es", "CL"), p.amount.toLong()) + " CLP" else "US$" + "%.2f".format(java.util.Locale.US, p.amount)
}

@Composable fun PlansScreen(user: User, session: SessionViewModel, vm: PlansViewModel = hiltViewModel()) {
    val plans by vm.plans.collectAsState(); val event by vm.event.collectAsState()
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.load(); vm.loadProduct(); vm.collectBilling { session.setUser(it) } }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(event) { event?.let { host.showSnackbar(it); vm.clearEvent() } }
    Scaffold(snackbarHost = { SnackbarHost(host) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Planes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("El plan Pro se activa sólo después de que el servidor verifica la compra con Google Play.", color = Gray, fontSize = 12.sp)
            StateView(plans, { vm.load() }) { list ->
                list.forEach { p ->
                    Section("${p.name}${if (user.plan.code == p.code) "  ✔ Tu plan" else ""}") {
                        Text(if (p.code == "FREE") "$0" else priceFor(p, user.country) + " / mes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("• Análisis por día: ${p.dailyAnalysisLimit ?: "Ilimitados"}")
                        Text("• Mercados: ${if (p.code == "FREE") "básicos (1X2, doble oportunidad, 2.5 goles, ambos marcan)" else "todos (goles, córners, tarjetas, NBA)"}")
                        Text("• Marcadores probables: ${if (p.code == "FREE") "el más probable" else "top 5 + rangos NBA"}")
                        Text("• Selecciones y filtros: ${if (p.selectionsEnabled) "sí" else "—"}")
                        Text("• Favoritos: ${p.favoritesLimit} · Historial: ${p.historyDays?.let { "$it días" } ?: "completo"}")
                        if (p.code == "PRO" && !user.isPro) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { scope.launch { vm.billing.launch(activity) } }, modifier = Modifier.fillMaxWidth()) { Text("Suscribirme con Google Play") }
                            OutlinedButton(onClick = { vm.restore() }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar compras") }
                            if (BuildConfig.DEMO_TOOLS) OutlinedButton(onClick = { vm.demoActivate { session.setUser(it) } }, modifier = Modifier.fillMaxWidth()) { Text("Activar Pro de PRUEBA (simulador local)") }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun SubscriptionScreen(user: User, vm: PlansViewModel = hiltViewModel(), onPlans: () -> Unit) {
    val sub by vm.subscription.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Suscripción", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Section("Estado") {
            Text("Plan actual: ${user.plan.name} · estado: ${user.subscriptionStatus ?: "sin suscripción"}")
            user.subscriptionEndsAt?.let { Text("Acceso hasta: ${it.take(10)}") }
            if (user.isPro) Text("Para cancelar usa Google Play → Suscripciones. Conservas Pro hasta el fin del periodo pagado.", color = Gray, fontSize = 12.sp)
            else Button(onClick = onPlans) { Text("Ver plan Pro") }
        }
        StateView(sub, { vm.load() }) { j ->
            val list = j.arr("subscriptions")
            if (list.isNotEmpty()) Section("Historial") { list.forEach { Text("${it.str("provider")} · ${it.str("status")} · fin ${it.str("current_period_end")?.take(10) ?: "—"}${if (it.bool("is_test") == true) " (prueba)" else ""}", fontSize = 13.sp) } }
        }
    }
}

@Composable fun ProfileScreen(user: User, session: SessionViewModel, onSettings: () -> Unit, onSubscription: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }; var delPass by remember { mutableStateOf("") }; var delError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Section(user.displayName) {
            Text(user.email); Text("País: ${user.country} · Idioma: ${user.language} · Zona horaria: ${user.timezone}")
            Text("Plan: ${user.plan.name} · correo ${if (user.emailVerified) "verificado ✔" else "sin verificar"}")
            Text("Consultas hoy: ${user.usage.used}${user.usage.limit?.let { " / $it" } ?: ""}", color = Gray)
        }
        Button(onClick = onSubscription, Modifier.fillMaxWidth()) { Text("Mi suscripción") }
        OutlinedButton(onClick = onSettings, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Configuración") }
        OutlinedButton(onClick = { confirmLogout = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cerrar sesión") }
        OutlinedButton(onClick = { deleting = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Eliminar mi cuenta y datos", color = MaterialTheme.colorScheme.error) }
        if (user.isAdmin) Text("El panel administrativo está disponible en la versión web.", color = Gray, fontSize = 12.sp)
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Eliminar cuenta") },
        text = { Column { Text("Se borrarán tu perfil, favoritos, selecciones e historial de forma permanente. Si tienes una suscripción, cancélala en Google Play."); OutlinedTextField(delPass, { delPass = it }, label = { Text("Tu contraseña") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); delError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { session.deleteAccount(delPass) { delError = it } }) { Text("Eliminar definitivamente") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } })
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Cerrar sesión") }, text = { Text("¿Seguro que quieres salir?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; session.logout() }) { Text("Salir") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } })
}

@Composable fun SettingsScreen(session: SessionViewModel, onPrivacy: () -> Unit, onTerms: () -> Unit, onHelp: () -> Unit) {
    val theme by session.theme.collectAsState()
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Section("Tema") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("light" to "Claro", "dark" to "Oscuro", "system" to "Sistema").forEach { (k, l) -> FilterChip(theme == k, { session.setTheme(k) }, label = { Text(l) }) } } }
        Section("Información") { Text("Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"); Text("Servidor: ${BuildConfig.API_BASE_URL}", color = Gray, fontSize = 12.sp) }
        TextButton(onClick = onPrivacy) { Text("Política de privacidad") }; TextButton(onClick = onTerms) { Text("Términos de servicio") }; TextButton(onClick = onHelp) { Text("Ayuda y soporte") }
    }
}

@Composable private fun Doc(title: String, body: List<Pair<String, String>>) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Borrador de trabajo: requiere revisión legal antes de publicar.", color = Gray, fontSize = 12.sp)
        body.forEach { (h, t) -> Text(h, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)); Text(t) }
    }
}
@Composable fun PrivacyScreen() = Doc("Política de privacidad", listOf(
    "Datos que tratamos" to "Correo, nombre opcional, país, idioma, zona horaria, historial de partidos analizados, favoritos, selecciones y estado de suscripción (nunca números de tarjeta).",
    "Finalidad" to "Prestar el servicio, aplicar límites del plan y verificar suscripciones con Google Play.",
    "Seguridad" to "Contraseñas con Argon2id, HTTPS, tokens revocables, tokens guardados con cifrado del Keystore de Android.",
    "Tus derechos" to "Puedes acceder, corregir y eliminar tu cuenta y datos asociados.",
    "Terceros" to "Datos deportivos de API-SPORTS, Google Play Billing y Firebase Cloud Messaging cuando se configuren."))
@Composable fun TermsScreen() = Doc("Términos de servicio", listOf(
    "Naturaleza" to "PronoScore360 es informativa. No es una casa de apuestas, no acepta depósitos ni apuestas ni enlaza a operadores.",
    "Sin garantías" to "Las probabilidades son estimaciones y pueden fallar. No prometemos aciertos.",
    "Planes" to "Gratis: 5 partidos analizados al día. Pro: suscripción mensual gestionada por Google Play, con renovación automática hasta cancelarla.",
    "Datos" to "Los datos provienen de terceros y pueden contener errores. En modo DEMO los datos son ficticios."))
@Composable fun HelpScreen() = Doc("Ayuda y soporte", listOf(
    "¿Qué cuenta como una consulta?" to "Analizar un partido cuenta una vez al día, sin importar cuántos mercados revises.",
    "¿Cuándo se renuevan?" to "A las 00:00 de la zona horaria de tu cuenta, calculado por el servidor.",
    "¿Datos insuficientes?" to "El modelo exige al menos 5 partidos previos por equipo. Preferimos no publicar antes que inventar.",
    "Soporte" to "soporte@pronoscore360.example (reemplazar por el correo real antes de publicar)."))
