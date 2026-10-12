package com.pronoscore360.app.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pronoscore360.app.R
import com.pronoscore360.app.ui.screens.*
import com.pronoscore360.app.vm.SessionViewModel

object R_ {
    const val SPLASH = "splash"; const val WELCOME = "welcome"; const val LOGIN = "login"; const val REGISTER = "register"; const val FORGOT = "forgot"
    const val HOME = "home"; const val SPORTS = "sports"; const val FOOTBALL = "football"; const val NBA = "nba"; const val TEAMS = "teams"
    const val MATCH = "match/{id}"; const val PREDICTIONS = "predictions"; const val SELECTIONS = "selections"; const val FAVORITES = "favorites"
    const val HISTORY = "history"; const val NOTIFICATIONS = "notifications"; const val PLANS = "plans"; const val SUBSCRIPTION = "subscription"
    const val PROFILE = "profile"; const val SETTINGS = "settings"; const val PRIVACY = "privacy"; const val TERMS = "terms"; const val HELP = "help"
}
private data class Tab(val route: String, val label: String, val icon: String)
private val tabs = listOf(Tab(R_.HOME, "Inicio", "🏠"), Tab(R_.FOOTBALL, "Fútbol", "⚽"), Tab(R_.NBA, "NBA", "🏀"), Tab(R_.PREDICTIONS, "Pronósticos", "📈"), Tab(R_.PROFILE, "Perfil", "👤"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(session: SessionViewModel, nav: NavHostController = rememberNavController()) {
    val user by session.user.collectAsState(); val booting by session.booting.collectAsState(); val demo by session.demoMode.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // La sesión manda: sin usuario -> pantallas públicas; con usuario -> inicio
    LaunchedEffect(user, booting) {
        if (booting) return@LaunchedEffect
        val publicRoutes = setOf(R_.SPLASH, R_.WELCOME, R_.LOGIN, R_.REGISTER, R_.FORGOT, R_.PRIVACY, R_.TERMS)
        if (user == null && route !in publicRoutes) nav.navigate(R_.WELCOME) { popUpTo(0) }
        else if (user != null && route in setOf(R_.SPLASH, R_.WELCOME, R_.LOGIN, R_.REGISTER, R_.FORGOT)) nav.navigate(R_.HOME) { popUpTo(0) }
    }
    val showBars = user != null && route != null && route !in setOf(R_.SPLASH, R_.WELCOME, R_.LOGIN, R_.REGISTER, R_.FORGOT)
    fun go(r: String) = nav.navigate(r) { launchSingleTop = true }
    Scaffold(
        topBar = { if (showBars && demo) Surface(color = androidx.compose.ui.graphics.Color(0xFFFFF4D6)) { Text("⚠ Modo DEMO: los datos deportivos son ficticios.", Modifier.fillMaxWidth().padding(6.dp), color = androidx.compose.ui.graphics.Color(0xFF5C4300), style = MaterialTheme.typography.labelMedium) } },
        bottomBar = {
            if (showBars) NavigationBar { tabs.forEach { t -> NavigationBarItem(selected = route == t.route, onClick = { nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Text(t.icon) }, label = { Text(t.label) }) } }
        },
    ) { pad ->
        NavHost(nav, startDestination = R_.SPLASH, modifier = Modifier.padding(pad)) {
            composable(R_.SPLASH) { SplashScreen() }
            composable(R_.WELCOME) { WelcomeScreen({ go(R_.LOGIN) }, { go(R_.REGISTER) }) }
            composable(R_.LOGIN) { LoginScreen(session, { go(R_.FORGOT) }, { go(R_.REGISTER) }) }
            composable(R_.REGISTER) { RegisterScreen(session, { go(R_.LOGIN) }, { go(R_.TERMS) }) }
            composable(R_.FORGOT) { ForgotScreen(session) { nav.popBackStack() } }
            composable(R_.PRIVACY) { PrivacyScreen() }; composable(R_.TERMS) { TermsScreen() }; composable(R_.HELP) { HelpScreen() }
            composable(R_.HOME) { Authed(user) { u -> HomeScreen(u, { go("match/$it") }, { go(if (it == "football") R_.FOOTBALL else R_.NBA) }, { go(R_.PLANS) }) } }
            composable(R_.SPORTS) { Authed(user) { SportSelectScreen { go(if (it == "football") R_.FOOTBALL else R_.NBA) } } }
            composable(R_.FOOTBALL) { Authed(user) { CalendarScreen("football", { go("match/$it") }) } }
            composable(R_.NBA) { Authed(user) { CalendarScreen("basketball", { go("match/$it") }) } }
            composable(R_.TEAMS) { Authed(user) { TeamsScreen { go("match/$it") } } }
            composable(R_.MATCH, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> Authed(user) { u -> MatchScreen(e.arguments!!.getLong("id"), u, session, { go(R_.PLANS) }) } }
            composable(R_.PREDICTIONS) { Authed(user) { u -> PredictionsScreen(u, { go("match/$it") }, { go(R_.PLANS) }) } }
            composable(R_.SELECTIONS) { Authed(user) { u -> SelectionsScreen(u) { go(R_.PLANS) } } }
            composable(R_.FAVORITES) { Authed(user) { u -> FavoritesScreen(u) { go("match/$it") } } }
            composable(R_.HISTORY) { Authed(user) { u -> HistoryScreen(u, { go("match/$it") }, { go(R_.PLANS) }) } }
            composable(R_.NOTIFICATIONS) { Authed(user) { NotificationsScreen() } }
            composable(R_.PLANS) { Authed(user) { u -> PlansScreen(u, session) } }
            composable(R_.SUBSCRIPTION) { Authed(user) { u -> SubscriptionScreen(u, onPlans = { go(R_.PLANS) }) } }
            composable(R_.PROFILE) { Authed(user) { u -> MoreMenu(u, session, ::go) } }
            composable(R_.SETTINGS) { Authed(user) { SettingsScreen(session, { go(R_.PRIVACY) }, { go(R_.TERMS) }, { go(R_.HELP) }) } }
        }
    }
}

@Composable private fun Authed(user: com.pronoscore360.core.model.User?, content: @Composable (com.pronoscore360.core.model.User) -> Unit) { if (user != null) content(user) }

/** Pestaña «Perfil»: perfil + accesos al resto de pantallas (favoritos, historial, selecciones, equipos…). */
@Composable private fun MoreMenu(u: com.pronoscore360.core.model.User, session: SessionViewModel, go: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn {
        item { ProfileScreen(u, session, { go(R_.SETTINGS) }, { go(R_.SUBSCRIPTION) }) }
        listOf("Equipos" to R_.TEAMS, "Deportes" to R_.SPORTS, "Selecciones y combinadas" to R_.SELECTIONS, "Favoritos" to R_.FAVORITES, "Historial y comparación" to R_.HISTORY,
            "Notificaciones" to R_.NOTIFICATIONS, "Planes" to R_.PLANS, "Ayuda y soporte" to R_.HELP, "Privacidad" to R_.PRIVACY, "Términos" to R_.TERMS).forEach { (l, r) ->
            item { ListItem(headlineContent = { Text(l) }, modifier = Modifier.clickable { go(r) }) }
        }
    }
}
