package cl.driverlink.app.navigation

import android.net.Uri

/** Rutas centralizadas. Ninguna pantalla construye rutas a mano. */
object Routes {
    const val SPLASH = "splash"

    // Grafo de autenticación
    const val AUTH_GRAPH = "auth"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Grafo principal
    const val MAIN_GRAPH = "main"
    const val HOME = "home"
    const val MAP = "map"
    const val RADIO = "radio?channelId={channelId}&companyId={companyId}"
    const val CHANNELS = "channels"
    const val PROFILE = "profile"

    const val VERIFICATION = "verification"
    const val CREATE_ALERT = "alerts/create"
    const val ALERT_DETAIL = "alerts/{alertId}"
    const val CHAT = "chat/{channelId}?companyId={companyId}"
    const val SOS = "sos"
    const val ACTIVE_SOS = "sos/active/{sosId}"
    const val SUBSCRIPTION = "subscription"
    const val EDIT_PROFILE = "profile/edit"
    const val EMERGENCY_CONTACTS = "emergency-contacts"
    const val NOTIFICATION_SETTINGS = "settings/notifications"
    const val PRIVACY = "settings/privacy"

    // Grafo de empresa (DriverLink Flotas)
    const val COMPANY_GRAPH = "company"
    const val COMPANY_ENTRY = "company/entry"
    const val CREATE_COMPANY = "company/create"
    const val JOIN_COMPANY = "company/join"
    const val COMPANY_HOME = "company/{companyId}/home"
    const val COMPANY_MEMBERS = "company/{companyId}/members"
    const val VEHICLES = "company/{companyId}/vehicles"

    const val ARG_ALERT_ID = "alertId"
    const val ARG_CHANNEL_ID = "channelId"
    const val ARG_COMPANY_ID = "companyId"
    const val ARG_SOS_ID = "sosId"

    fun radio(channelId: String? = null, companyId: String? = null): String = buildString {
        append("radio")
        val params = listOfNotNull(
            channelId?.let { "channelId=${Uri.encode(it)}" },
            companyId?.let { "companyId=${Uri.encode(it)}" },
        )
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }

    fun alertDetail(alertId: String) = "alerts/${Uri.encode(alertId)}"
    fun chat(channelId: String, companyId: String? = null) =
        "chat/${Uri.encode(channelId)}" + (companyId?.let { "?companyId=${Uri.encode(it)}" } ?: "")
    fun activeSos(sosId: String) = "sos/active/${Uri.encode(sosId)}"
    fun companyHome(companyId: String) = "company/${Uri.encode(companyId)}/home"
    fun companyMembers(companyId: String) = "company/${Uri.encode(companyId)}/members"
    fun vehicles(companyId: String) = "company/${Uri.encode(companyId)}/vehicles"
}
