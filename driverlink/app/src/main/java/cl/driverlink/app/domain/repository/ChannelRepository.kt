package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    /** Canales comunitarios activos del modo indicado, ordenados para mostrar. */
    fun observeCommunityChannels(mode: ChannelMode): Flow<List<Channel>>
    fun observeCompanyChannels(companyId: String, mode: ChannelMode): Flow<List<Channel>>
    fun observeChannel(key: ChannelKey): Flow<Channel?>
    suspend fun createCompanyChannel(companyId: String, name: String, mode: ChannelMode): AppResult<String>
}

interface MessageRepository {
    /** Escucha solo los últimos [limit] mensajes del canal (nunca el historial completo). */
    fun observeLatest(key: ChannelKey, limit: Int): Flow<List<Message>>
    /** Página de mensajes anteriores a [beforeCreatedAt], en orden descendente. */
    suspend fun loadOlder(key: ChannelKey, beforeCreatedAt: Long, limit: Int): AppResult<List<Message>>
    suspend fun sendText(key: ChannelKey, text: String, replyTo: Message?): AppResult<Unit>
    suspend fun sendAudio(key: ChannelKey, clip: AudioClip): AppResult<Unit>
    suspend fun deleteOwn(key: ChannelKey, messageId: String): AppResult<Unit>
}
