package cl.driverlink.app.domain.model

enum class NotificationCategory(val defaultEnabled: Boolean) {
    NEARBY_ALERTS(true),
    REPLIES(true),
    CHANNEL_ACTIVITY(false),
    SOS_UPDATES(true),
    ADMIN(true),
    SUBSCRIPTION(true),
}

data class NotificationPreferences(val enabled: Map<NotificationCategory, Boolean>) {
    fun isEnabled(category: NotificationCategory): Boolean =
        enabled[category] ?: category.defaultEnabled

    fun with(category: NotificationCategory, value: Boolean) =
        copy(enabled = enabled + (category to value))

    companion object {
        val DEFAULT = NotificationPreferences(NotificationCategory.entries.associateWith { it.defaultEnabled })
    }
}
