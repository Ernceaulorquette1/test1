package cl.driverlink.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import cl.driverlink.app.presentation.alerts.AlertDetailScreen
import cl.driverlink.app.presentation.alerts.CreateAlertScreen
import cl.driverlink.app.presentation.auth.LoginScreen
import cl.driverlink.app.presentation.auth.RegisterScreen
import cl.driverlink.app.presentation.auth.VerificationScreen
import cl.driverlink.app.presentation.auth.WelcomeScreen
import cl.driverlink.app.presentation.chat.ChannelsScreen
import cl.driverlink.app.presentation.chat.ChatScreen
import cl.driverlink.app.presentation.company.CompanyEntryScreen
import cl.driverlink.app.presentation.company.CompanyHomeScreen
import cl.driverlink.app.presentation.company.CompanyMembersScreen
import cl.driverlink.app.presentation.company.CreateCompanyScreen
import cl.driverlink.app.presentation.company.JoinCompanyScreen
import cl.driverlink.app.presentation.company.VehiclesScreen
import cl.driverlink.app.presentation.home.HomeScreen
import cl.driverlink.app.presentation.map.MapScreen
import cl.driverlink.app.presentation.notifications.NotificationSettingsScreen
import cl.driverlink.app.presentation.profile.EditProfileScreen
import cl.driverlink.app.presentation.profile.ProfileScreen
import cl.driverlink.app.presentation.radio.RadioScreen
import cl.driverlink.app.presentation.settings.PrivacyScreen
import cl.driverlink.app.presentation.sos.ActiveSosScreen
import cl.driverlink.app.presentation.sos.EmergencyContactsScreen
import cl.driverlink.app.presentation.sos.SosScreen
import cl.driverlink.app.presentation.splash.SplashScreen
import cl.driverlink.app.presentation.subscription.SubscriptionScreen

private val optionalString = { name: String ->
    navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
}

@Composable
fun DriverLinkNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.SPLASH, modifier = modifier) {
        composable(Routes.SPLASH) { SplashScreen() }

        navigation(route = Routes.AUTH_GRAPH, startDestination = Routes.WELCOME) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onRegister = { navController.navigate(Routes.REGISTER) },
                )
            }
            composable(Routes.LOGIN) { LoginScreen(onBack = back, onRegister = { navController.navigate(Routes.REGISTER) }) }
            composable(Routes.REGISTER) { RegisterScreen(onBack = back) }
        }

        navigation(route = Routes.MAIN_GRAPH, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenMap = { navController.navigate(Routes.MAP) },
                    onOpenRadio = { navController.navigate(Routes.radio()) },
                    onOpenChat = { navController.navigate(Routes.CHANNELS) },
                    onReport = { navController.navigate(Routes.CREATE_ALERT) },
                    onSos = { navController.navigate(Routes.SOS) },
                    onAlert = { navController.navigate(Routes.alertDetail(it)) },
                    onChannel = { navController.navigate(Routes.chat(it)) },
                    onVerify = { navController.navigate(Routes.VERIFICATION) },
                )
            }
            composable(Routes.MAP) {
                MapScreen(onAlertDetail = { navController.navigate(Routes.alertDetail(it)) })
            }
            composable(
                Routes.RADIO,
                arguments = listOf(optionalString(Routes.ARG_CHANNEL_ID), optionalString(Routes.ARG_COMPANY_ID)),
            ) { RadioScreen() }
            composable(Routes.CHANNELS) {
                ChannelsScreen(
                    onOpenChannel = { channelId, companyId -> navController.navigate(Routes.chat(channelId, companyId)) },
                    onOpenCompany = { navController.navigate(Routes.COMPANY_GRAPH) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                    onEmergencyContacts = { navController.navigate(Routes.EMERGENCY_CONTACTS) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onVerification = { navController.navigate(Routes.VERIFICATION) },
                    onCompany = { navController.navigate(Routes.COMPANY_GRAPH) },
                )
            }
            composable(Routes.VERIFICATION) { VerificationScreen(onBack = back) }
            composable(Routes.CREATE_ALERT) {
                CreateAlertScreen(onBack = back, onCreated = {
                    navController.navigate(Routes.MAP) { popUpTo(Routes.CREATE_ALERT) { inclusive = true } }
                })
            }
            composable(Routes.ALERT_DETAIL, arguments = listOf(navArgument(Routes.ARG_ALERT_ID) { type = NavType.StringType })) {
                AlertDetailScreen(onBack = back)
            }
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType },
                    optionalString(Routes.ARG_COMPANY_ID),
                ),
            ) { ChatScreen(onBack = back) }
            composable(Routes.SOS) {
                SosScreen(
                    onBack = back,
                    onSeePlans = { navController.navigate(Routes.SUBSCRIPTION) },
                    onSosActive = { id ->
                        navController.navigate(Routes.activeSos(id)) { popUpTo(Routes.SOS) { inclusive = true } }
                    },
                )
            }
            composable(Routes.ACTIVE_SOS, arguments = listOf(navArgument(Routes.ARG_SOS_ID) { type = NavType.StringType })) {
                ActiveSosScreen(onBack = back)
            }
            composable(Routes.SUBSCRIPTION) { SubscriptionScreen(onBack = back) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = back) }
            composable(Routes.EMERGENCY_CONTACTS) {
                EmergencyContactsScreen(onBack = back, onSeePlans = { navController.navigate(Routes.SUBSCRIPTION) })
            }
            composable(Routes.NOTIFICATION_SETTINGS) { NotificationSettingsScreen(onBack = back) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }
        }

        navigation(route = Routes.COMPANY_GRAPH, startDestination = Routes.COMPANY_ENTRY) {
            composable(Routes.COMPANY_ENTRY) {
                CompanyEntryScreen(
                    onBack = back,
                    onCreate = { navController.navigate(Routes.CREATE_COMPANY) },
                    onJoin = { navController.navigate(Routes.JOIN_COMPANY) },
                    onOpenCompany = { navController.navigate(Routes.companyHome(it)) },
                )
            }
            composable(Routes.CREATE_COMPANY) {
                CreateCompanyScreen(onBack = back, onCreated = { id ->
                    navController.navigate(Routes.companyHome(id)) { popUpTo(Routes.COMPANY_ENTRY) }
                })
            }
            composable(Routes.JOIN_COMPANY) { JoinCompanyScreen(onBack = back) }
            composable(Routes.COMPANY_HOME, arguments = listOf(navArgument(Routes.ARG_COMPANY_ID) { type = NavType.StringType })) {
                CompanyHomeScreen(
                    onBack = back,
                    onMembers = { navController.navigate(Routes.companyMembers(it)) },
                    onVehicles = { navController.navigate(Routes.vehicles(it)) },
                    onChannel = { channelId, companyId -> navController.navigate(Routes.chat(channelId, companyId)) },
                    onRadio = { channelId, companyId -> navController.navigate(Routes.radio(channelId, companyId)) },
                )
            }
            composable(Routes.COMPANY_MEMBERS, arguments = listOf(navArgument(Routes.ARG_COMPANY_ID) { type = NavType.StringType })) {
                CompanyMembersScreen(onBack = back)
            }
            composable(Routes.VEHICLES, arguments = listOf(navArgument(Routes.ARG_COMPANY_ID) { type = NavType.StringType })) {
                VehiclesScreen(onBack = back)
            }
        }
    }
}
