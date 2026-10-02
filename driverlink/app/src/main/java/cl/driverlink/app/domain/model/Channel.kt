package cl.driverlink.app.domain.model

enum class ChannelType { PUBLIC, COMMUNITY, PRIVATE_COMPANY, EMERGENCY }

/** Un canal es de texto (chat) o de voz (radio walkie-talkie). */
enum class ChannelMode { TEXT, RADIO }

/**
 * Identifica un canal. Los canales de empresa viven bajo `companies/{companyId}`,
 * separados físicamente de la comunidad.
 */
data class ChannelKey(val id: String, val companyId: String? = null) {
    val isCompany: Boolean get() = companyId != null
}

data class Channel(
    val id: String,
    val name: String,
    val description: String,
    val type: ChannelType,
    val mode: ChannelMode,
    val region: String,
    val city: String,
    val zone: String?,
    val companyId: String?,
    val order: Int,
    val isActive: Boolean,
    val lastMessageAt: Long?,
) {
    val key: ChannelKey get() = ChannelKey(id, companyId)
}
