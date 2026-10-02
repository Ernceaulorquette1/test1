package cl.driverlink.app.core.analytics

/**
 * Eventos de analítica. Nunca incluyen datos sensibles (ubicación exacta, teléfono,
 * correo, notas de SOS ni contenido de mensajes).
 */
enum class AnalyticsEvent(val eventName: String) {
    REGISTRATION_COMPLETED("registration_completed"),
    LOGIN_COMPLETED("login_completed"),
    CHANNEL_OPENED("channel_opened"),
    MESSAGE_SENT("message_sent"),
    ALERT_CREATED("alert_created"),
    ALERT_CONFIRMED("alert_confirmed"),
    RADIO_MESSAGE_SENT("radio_message_sent"),
    TRIAL_STARTED("trial_started"),
    SUBSCRIPTION_SCREEN_OPENED("subscription_screen_opened"),
    SOS_STARTED("sos_started"),
    SOS_CANCELLED("sos_cancelled"),
    SOS_RESOLVED("sos_resolved"),
    COMPANY_CREATED("company_created"),
    COMPANY_JOIN_REQUESTED("company_join_requested"),
    WORK_SESSION_STARTED("work_session_started"),
    CONTENT_REPORTED("content_reported"),
}

interface AnalyticsTracker {
    fun log(event: AnalyticsEvent, params: Map<String, String> = emptyMap())
    fun setUserId(userId: String?)
}
