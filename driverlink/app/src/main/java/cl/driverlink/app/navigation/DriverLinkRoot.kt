package cl.driverlink.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cl.driverlink.app.R
import cl.driverlink.app.domain.repository.AuthState
import cl.driverlink.app.presentation.common.appContainer
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.splash.MaintenanceScreen
import cl.driverlink.app.ui.components.OfflineBanner
import cl.driverlink.app.ui.theme.DriverLinkThemeExt

private data class TopLevelTab(val route: String, val navigateTo: String, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    TopLevelTab(Routes.HOME, Routes.HOME, Icons.Filled.Home, R.string.tab_home),
    TopLevelTab(Routes.MAP, Routes.MAP, Icons.Filled.Map, R.string.tab_map),
    TopLevelTab(Routes.RADIO, Routes.radio(), Icons.Filled.SettingsVoice, R.string.tab_radio),
    TopLevelTab(Routes.CHANNELS, Routes.CHANNELS, Icons.AutoMirrored.Filled.Chat, R.string.tab_chat),
    TopLevelTab(Routes.PROFILE, Routes.PROFILE, Icons.Filled.Person, R.string.tab_profile),
)

/** Rutas donde se muestran los accesos rápidos Reportar y SOS. */
private val quickActionRoutes = setOf(Routes.HOME, Routes.MAP, Routes.CHANNELS)

@Composable
fun DriverLinkRoot() {
    val sessionViewModel = appViewModel { SessionViewModel(it.authRepository, it.configRepository, it.analytics, it.crashReporter) }
    val authState by sessionViewModel.authState.collectAsStateWithLifecycle()
    val config by sessionViewModel.config.collectAsStateWithLifecycle()
    val isOnline by appContainer().connectivityObserver.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val navController = rememberNavController()

    // La sesión decide el grafo: sin sesión → Auth; con sesión → Main. Se limpia el back stack
    // para que "atrás" nunca regrese a pantallas de otra sesión.
    LaunchedEffect(authState) {
        val target = when (authState) {
            AuthState.Unknown -> return@LaunchedEffect
            AuthState.SignedOut -> Routes.AUTH_GRAPH
            is AuthState.SignedIn -> Routes.MAIN_GRAPH
        }
        val currentGraph = navController.currentBackStackEntry?.destination?.parent?.route
        if (currentGraph != target && !(target == Routes.MAIN_GRAPH && currentGraph == Routes.COMPANY_GRAPH)) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    if (config.maintenanceMode) {
        MaintenanceScreen()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        topBar = { if (!isOnline) OfflineBanner(Modifier.statusBarsPadding()) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBottomBar) BottomBar(navController, currentRoute) },
        floatingActionButton = {
            if (currentRoute in quickActionRoutes) {
                QuickActions(
                    onReport = { navController.navigate(Routes.CREATE_ALERT) },
                    onSos = { navController.navigate(Routes.SOS) },
                )
            }
        },
    ) { padding ->
        DriverLinkNavHost(navController, Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route ||
                navController.currentBackStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.navigateTo) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@Composable
private fun QuickActions(onReport: () -> Unit, onSos: () -> Unit) {
    val colors = DriverLinkThemeExt.colors
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallFloatingActionButton(
            onClick = onSos,
            containerColor = colors.sos,
            contentColor = colors.onSos,
        ) {
            Text("SOS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        }
        ExtendedFloatingActionButton(
            onClick = onReport,
            icon = { Icon(Icons.Filled.AddAlert, contentDescription = null) },
            text = { Text(stringResource(R.string.action_report)) },
        )
    }
}
